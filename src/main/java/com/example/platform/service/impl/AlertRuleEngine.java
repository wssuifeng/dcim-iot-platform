package com.example.platform.service.impl;

import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.dto.AlertRuleDefinition;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceData;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.DictMetric;
import com.example.platform.service.AlertService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import com.example.platform.service.DictMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/*
 * 类功能:告警规则引擎，基于设备级/类型级配置的规则对设备数据进行评估，触发告警生成或升级
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleEngine {

    private final DeviceService deviceService;
    private final DeviceTypeService deviceTypeService;
    private final AlertService alertService;
    private final DictMetricService dictMetricService;

    /*
     * 方法功能:评估指定设备的采集数据是否触发告警规则
     * 参数:deviceId-设备ID, deviceData-设备采集数据
     * 返回数据:无
     * */
    public void evaluate(Integer deviceId, DeviceData deviceData) {
        // 1. 获取生效的告警规则配置（设备级优先，类型级降级）
        AlertRuleConfig effectiveConfig = getEffectiveRuleConfig(deviceId);
        if (effectiveConfig == null || effectiveConfig.getRules() == null
                || effectiveConfig.getRules().isEmpty()) {
            return;
        }

        // 2. 匹配当前指标对应的规则
        String metric = deviceData.getMetric();
        AlertRuleDefinition matchedRule = null;
        for (AlertRuleDefinition rule : effectiveConfig.getRules()) {
            if (rule.getMetricKey().equals(metric)) {
                matchedRule = rule;
                break;
            }
        }
        if (matchedRule == null) {
            return;
        }

        // 3. 判断数据值是否超过阈值
        if (!isThresholdExceeded(deviceData.getDataValue(), matchedRule)) {
            return;
        }

        // 4. 生成告警或升级已有告警
        createOrUpgradeAlert(deviceId, matchedRule, deviceData);
    }

    /*
     * 方法功能:获取生效的告警规则配置，设备级配置优先于类型级配置
     * 参数:deviceId-设备ID
     * 返回数据:AlertRuleConfig-生效的告警规则配置；设备级和类型级均无配置时返回null
     * */
    private AlertRuleConfig getEffectiveRuleConfig(Integer deviceId) {
        // 优先使用设备级配置
        Device device = deviceService.getDeviceOrThrow(deviceId);
        AlertRuleConfig deviceConfig = device.getAlertRuleConfig();
        if (deviceConfig != null && deviceConfig.getRules() != null
                && !deviceConfig.getRules().isEmpty()) {
            return deviceConfig;
        }

        // 降级使用类型级配置
        DeviceType type = deviceTypeService.getTypeOrThrow(device.getTypeId());
        AlertRuleConfig typeConfig = type.getAlertRuleConfig();
        if (typeConfig != null && typeConfig.getRules() != null
                && !typeConfig.getRules().isEmpty()) {
            return typeConfig;
        }

        return null;
    }

    /*
     * 方法功能:判断数据值是否超过规则定义的阈值
     * 参数:value-实际数据值, rule-告警规则定义
     * 返回数据:boolean-超过阈值返回true，否则返回false
     * */
    private boolean isThresholdExceeded(BigDecimal value, AlertRuleDefinition rule) {
        double val = value.doubleValue();
        double threshold = rule.getThreshold();

        return switch (rule.getOperator()) {
            case "GT" -> val > threshold;
            case "LT" -> val < threshold;
            case "GTE" -> val >= threshold;
            case "LTE" -> val <= threshold;
            case "EQ" -> Double.compare(val, threshold) == 0;
            default -> false;
        };
    }

    /*
     * 方法功能:创建新告警或升级已有未处理告警的级别
     * 参数:deviceId-设备ID, rule-匹配的告警规则, deviceData-触发告警的设备数据
     * 返回数据:无
     * */
    private void createOrUpgradeAlert(Integer deviceId, AlertRuleDefinition rule, DeviceData deviceData) {
        // 查找该设备同类型且未处理的告警
        List<Alert> existingAlerts = alertService.findByDeviceId(deviceId).stream()
                .filter(a -> a.getAlertType().equals(rule.getMetricKey()) && a.getIsHandled() == 0)
                .toList();

        if (existingAlerts.isEmpty()) {
            // 无已有告警，创建新告警
            Alert alert = new Alert();
            alert.setDeviceId(deviceId);
            alert.setAlertType(rule.getMetricKey());
            alert.setAlertLevel(rule.getLevel());
            alert.setAlertMessage(buildAlertMessage(rule, deviceData));
            alert.setIsHandled(0);
            alertService.save(alert);
            log.info("🚨 生成告警: 设备={}, 类型={}, 级别={}", deviceId, rule.getMetricKey(), rule.getLevel());
        } else {
            // 已有告警，判断是否需要升级（级别数字越小越紧急）
            Alert existingAlert = existingAlerts.get(0);
            if (rule.getLevel() < existingAlert.getAlertLevel()) {
                int oldLevel = existingAlert.getAlertLevel();
                existingAlert.setAlertLevel(rule.getLevel());
                existingAlert.setAlertMessage(buildAlertMessage(rule, deviceData));
                alertService.updateById(existingAlert);
                log.info("⬆️ 告警升级: 设备={}, 类型={}, 旧级别={}, 新级别={}",
                        deviceId, rule.getMetricKey(), oldLevel, rule.getLevel());
            }
        }
    }

    /*
     * 方法功能:根据规则模板和实际数据值构建告警消息
     * 参数:rule-告警规则定义, deviceData-设备数据
     * 返回数据:String-格式化后的告警消息
     * */
    private String buildAlertMessage(AlertRuleDefinition rule, DeviceData deviceData) {
        String metricName = rule.getMetricKey();
        String metricUnit = "";
        try {
            DictMetric metricInfo = dictMetricService.getFromCache(rule.getMetricKey());
            if (metricInfo != null) {
                if (StringUtils.hasText(metricInfo.getMetricName())) {
                    metricName = metricInfo.getMetricName();
                }
                if (StringUtils.hasText(metricInfo.getMetricUnit())) {
                    metricUnit = metricInfo.getMetricUnit();
                }
            }
        } catch (Exception e) {
            log.warn("获取指标字典失败，使用原始metricKey: {}", rule.getMetricKey());
        }

        String template = rule.getMessageTemplate();
        if (template != null && !template.isEmpty()) {
            return template
                    .replace("{value}", deviceData.getDataValue().toString())
                    .replace("{threshold}", rule.getThreshold().toString())
                    .replace("{metricName}", metricName)
                    .replace("{unit}", metricUnit);
        }
        return metricName + "当前值" + deviceData.getDataValue() + metricUnit + "，超过阈值" + rule.getThreshold() + metricUnit;
    }
}
