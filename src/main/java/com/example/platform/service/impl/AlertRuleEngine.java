package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 告警规则引擎
 * <p>
 * 核心运行流程：
 * 1. 找生效规则（设备级优先，没有就找类型级）
 * 2. 过滤出匹配当前指标的规则，按告警级别从高到低排序
 * 3. 评估是否触发阈值
 * 4. 防重与升级：如果有活跃告警就不重复报，如果新规则级别更高就升级告警
 * 5. 批量保存新产生的告警
 */
@Slf4j
@Component
public class AlertRuleEngine {

    private final AlertService alertService;
    private final DeviceService deviceService;
    private final DeviceTypeService deviceTypeService;
    private final DictMetricService dictMetricService;

    public AlertRuleEngine(AlertService alertService, DeviceService deviceService,
                           DeviceTypeService deviceTypeService, DictMetricService dictMetricService) {
        this.alertService = alertService;
        this.deviceService = deviceService;
        this.deviceTypeService = deviceTypeService;
        this.dictMetricService = dictMetricService;
    }

    /**
     * 评估设备数据是否触发告警
     */
    public void evaluate(Integer deviceId, DeviceData data) {

        // ===== 1. 基础校验 =====
        BigDecimal value = data.getDataValue();
        if (value == null) {
            return;
        }
        Device device = deviceService.getDeviceOrThrow(deviceId);

        // ===== 2. 获取规则并过滤排序 =====
        AlertRuleConfig effectiveConfig = getEffectiveRuleConfig(device);
        if (effectiveConfig == null || effectiveConfig.getRules() == null || effectiveConfig.getRules().isEmpty()) {
            return;
        }

        // 先过滤出指标匹配的规则，再按级别降序排序。
        // 这样做是为了保证后续分组遍历时，每个类型里最高级别的规则排在前面，碰到触发的就能直接 break，不用再往下找
        List<AlertRuleDefinition> sortedMatchedRules = effectiveConfig.getRules().stream()
                .filter(rule -> isMetricMatched(rule, data))
                .sorted(Comparator.comparingInt(AlertRuleDefinition::getLevel).reversed())
                .toList();

        if (sortedMatchedRules.isEmpty()) {
            return;
        }

        // ===== 3. 获取当前设备的活跃告警快照 =====
        // 优化：一次性查出所有活跃告警存入Map，避免下面循环里每条规则都要查一次数据库（N+1问题）
        Map<String, Alert> activeAlertMap = getActiveAlertMap(deviceId);

        // ===== 4. 按告警类型分组遍历评估 =====
        Map<String, List<AlertRuleDefinition>> groupedRules = sortedMatchedRules.stream()
                .collect(Collectors.groupingBy(rule -> StringUtils.hasText(rule.getAlertType()) ? rule.getAlertType() : "DEFAULT_TYPE"));

        List<Alert> pendingAlerts = new ArrayList<>();

        for (Map.Entry<String, List<AlertRuleDefinition>> entry : groupedRules.entrySet()) {
            String alertType = entry.getKey();
            List<AlertRuleDefinition> rulesOfType = entry.getValue();

            for (AlertRuleDefinition rule : rulesOfType) {
                if (evaluateRule(value, rule)) {
                    Alert existingAlert = activeAlertMap.get(alertType);

                    if (existingAlert == null) {
                        // 没有活跃告警，准备新增一条
                        pendingAlerts.add(buildAlertEntity(deviceId, rule, value));
                    } else {
                        // 已有活跃告警，如果新触发的规则级别更高，则升级现有告警
                        if (rule.getLevel() > existingAlert.getAlertLevel()) {
                            upgradeAlert(existingAlert.getId(), rule, value);
                        }
                    }
                    // 命中即止：当前类型下，只要最高级别的规则被触发了，就不再看低级别的规则了
                    break;
                }
            }
        }

        // ===== 5. 批量保存 =====
        if (!pendingAlerts.isEmpty()) {
            alertService.saveBatch(pendingAlerts);
            log.info("🚨 批量新增告警：deviceId={}, 数量={}", deviceId, pendingAlerts.size());

            // 【并发冲突说明】
            // 虽然上面通过 activeAlertMap 判断了 existingAlert == null 才新增，但在高并发场景下：
            // 假设同一瞬间来了两条数据，两个线程同时查活跃告警都为空，就会同时把新告警加入 pendingAlerts。
            // 当两个线程先后执行 saveBatch 时，后执行的就会因为数据库的唯一索引抛出 DuplicateKeyException。
            // 内存判重是为了挡住 99% 的常规请求，剩下的 1% 并发冲突需要依赖数据库唯一索引来兜底。
            // 生产环境中，建议在这里 try-catch 忽略 DuplicateKeyException，因为说明告警已经成功生成了。
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 一次性查出该设备所有未处理告警，转成 Map 方便按 alertType 快速查找
     */
    private Map<String, Alert> getActiveAlertMap(Integer deviceId) {
        List<Alert> activeAlerts = alertService.list(
                new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getDeviceId, deviceId)
                        .eq(Alert::getIsHandled, 0)
        );
        if (activeAlerts == null || activeAlerts.isEmpty()) {
            return Collections.emptyMap();
        }
        // 如果由于脏数据导致 alertType 重复，保留已有的那条
        return activeAlerts.stream()
                .collect(Collectors.toMap(Alert::getAlertType, alert -> alert, (existing, replacement) -> existing));
    }

    /**
     * 获取生效规则（设备自己配的优先，没配就用设备类型的）
     */
    private AlertRuleConfig getEffectiveRuleConfig(Device device) {
        if (device.getAlertRuleConfig() != null && !device.getAlertRuleConfig().getRules().isEmpty()) {
            return device.getAlertRuleConfig();
        }
        DeviceType type = deviceTypeService.getById(device.getTypeId());
        if (type != null && type.getAlertRuleConfig() != null && !type.getAlertRuleConfig().getRules().isEmpty()) {
            return type.getAlertRuleConfig();
        }
        return null;
    }

    /**
     * 判断数据指标是否匹配规则
     * <p>
     * 修正逻辑：如果规则明确要求了指标（比如只管温度），但数据里没带指标标识，
     * 那坚决不算匹配，防止乱七八糟的数据误触发特定指标的告警。
     */
    private boolean isMetricMatched(AlertRuleDefinition rule, DeviceData data) {
        if (!StringUtils.hasText(rule.getMetric())) return true; // 规则没限制指标，放行
        if (!StringUtils.hasText(data.getMetric())) return false; // 规则有限制，但数据没指标，拒绝匹配
        return rule.getMetric().equals(data.getMetric()); // 两者都有，必须精准相等
    }

    /**
     * 比较数值是否超过阈值
     */
    private boolean evaluateRule(BigDecimal value, AlertRuleDefinition rule) {
        if (rule.getThreshold() == null || !StringUtils.hasText(rule.getOperator())) return false;
        String op = rule.getOperator().trim();
        int result = value.compareTo(rule.getThreshold());
        return switch (op) {
            case ">" -> result > 0;
            case "<" -> result < 0;
            case ">=" -> result >= 0;
            case "<=" -> result <= 0;
            case "==" -> result == 0;
            default -> false;
        };
    }

    /**
     * 组装一个新的 警告（还没存库的）
     */
    private Alert buildAlertEntity(Integer deviceId, AlertRuleDefinition rule, BigDecimal value) {
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setAlertType(rule.getAlertType());
        alert.setAlertLevel(rule.getLevel());
        alert.setAlertMessage(buildAlertMessage(rule, value));
        alert.setIsHandled(0);
        return alert;
    }

    /**
     * 升级告警：直接更新数据库里的告警级别和消息内容
     */
    private void upgradeAlert(Integer existingAlertId, AlertRuleDefinition newRule, BigDecimal value) {
        LambdaUpdateWrapper<Alert> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Alert::getId, existingAlertId)
                .set(Alert::getAlertLevel, newRule.getLevel())
                .set(Alert::getAlertMessage, buildAlertMessage(newRule, value));
        alertService.update(updateWrapper);
        log.info("⬆️ 告警升级：alertId={}, 升级为 {}级", existingAlertId, newRule.getLevel());
    }

    /**
     * 生成告警消息，自动从字典服务拿中文指标名和单位
     * <p>
     * 降级策略：如果字典表里没配这个指标，或者缓存里没查到，
     * 就用指标的英文标识（metric）当中文名顶上，单位就空着，保证程序不报错，消息不至于完全看不懂。
     */
    private String buildAlertMessage(AlertRuleDefinition rule, BigDecimal value) {
        // 从字典缓存拿元数据
        DictMetric metricInfo = dictMetricService.getFromCache(rule.getMetric());

        // 提取中文名：没查到就用英文名顶替
        String metricName = (metricInfo != null && StringUtils.hasText(metricInfo.getMetricName()))
                ? metricInfo.getMetricName()
                : (StringUtils.hasText(rule.getMetric()) ? rule.getMetric() : "未知指标");

        // 提取单位：没查到就空着
        String metricUnit = (metricInfo != null && StringUtils.hasText(metricInfo.getMetricUnit()))
                ? metricInfo.getMetricUnit()
                : "";

        // 优先用规则里配的模板
        if (StringUtils.hasText(rule.getMessageTemplate())) {
            return rule.getMessageTemplate()
                    .replace("{metricName}", metricName)
                    .replace("{unit}", metricUnit)
                    .replace("{value}", value.toString())
                    .replace("{threshold}", rule.getThreshold().toString());
        }

        // 没配模板就用默认格式，拼出来大概长这样："温度 触发规则，当前值 85.5℃，阈值 > 80℃"
        return String.format("%s 触发规则，当前值 %s%s，阈值 %s %s%s",
                metricName, value, metricUnit, rule.getOperator(), rule.getThreshold(), metricUnit);
    }
}
