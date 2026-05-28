package com.example.platform.service.impl;

import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.CacheService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/*
 * 类功能:告警配置缓存服务实现类，管理设备级和类型级告警规则的Redis缓存
 *
 * 【缓存策略】
 * - 设备级Key: config:alert:device:{deviceId}，TTL=1h
 * - 类型级Key: config:alert:type:{typeId}，TTL=1h
 * - 读流程: 缓存 → 数据库 → 回填（Cache-Aside模式）
 * - 写策略: 先更新DB，再删除缓存
 * - 防穿透: DB无数据时存入NullValue占位（TTL=5min），读取时判断instanceof返回null
 *
 * 【告警配置的优先级】
 * 设备级配置 > 类型级配置（降级兜底）
 * 告警规则引擎(AlertRuleEngine)先查设备级，无配置时降级查类型级
 * 两级缓存独立管理，互不干扰
 * */
@Service
public class AlertConfigServiceImpl implements AlertConfigService {

    private static final String DEVICE_KEY_PREFIX = "config:alert:device:";
    private static final String TYPE_KEY_PREFIX = "config:alert:type:";
    private static final long CACHE_TTL_HOURS = 1;
    private static final long NULL_VALUE_TTL_MINUTES = 5;

    private final CacheService cacheService;
    private final DeviceService deviceService;
    private final DeviceTypeService deviceTypeService;

    public AlertConfigServiceImpl(CacheService cacheService,
                                  @Lazy DeviceService deviceService,
                                  @Lazy DeviceTypeService deviceTypeService) {
        this.cacheService = cacheService;
        this.deviceService = deviceService;
        this.deviceTypeService = deviceTypeService;
    }

    /*
     * 方法功能:从缓存获取设备级告警配置，未命中时查DB回填
     * 参数:deviceId-设备ID
     * 返回数据:AlertRuleConfig-设备级告警配置，无配置返回null
     *
     * 【读流程（Cache-Aside模式）】
     * 1. 缓存命中且非NullValue → 直接返回
     * 2. 缓存命中但是NullValue → 返回null（防穿透：该设备确实无告警配置）
     * 3. 缓存未命中 → 查询设备表 → 有配置存1h，无配置存NullValue占位5min
     * */
    @Override
    public AlertRuleConfig getDeviceAlertConfig(Integer deviceId) {
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;

        Object cached = cacheService.get(cacheKey, Object.class);
        if (cached != null) {
            if (cached instanceof CacheService.NullValue) {
                return null;
            }
            if (cached instanceof AlertRuleConfig) {
                return (AlertRuleConfig) cached;
            }
        }

        Device device = deviceService.getById(deviceId);
        AlertRuleConfig config = (device != null) ? device.getAlertRuleConfig() : null;

        if (config != null) {
            cacheService.set(cacheKey, config, CACHE_TTL_HOURS, TimeUnit.HOURS);
            return config;
        }

        cacheService.set(cacheKey, new CacheService.NullValue(), NULL_VALUE_TTL_MINUTES, TimeUnit.MINUTES);
        return null;
    }

    /*
     * 方法功能:从缓存获取类型级告警配置，未命中时查DB回填
     * 参数:typeId-设备类型ID
     * 返回数据:AlertRuleConfig-类型级告警配置，无配置返回null
     *
     * 【读流程同getDeviceAlertConfig】
     * */
    @Override
    public AlertRuleConfig getTypeAlertConfig(Integer typeId) {
        String cacheKey = TYPE_KEY_PREFIX + typeId;

        Object cached = cacheService.get(cacheKey, Object.class);
        if (cached != null) {
            if (cached instanceof CacheService.NullValue) {
                return null;
            }
            if (cached instanceof AlertRuleConfig) {
                return (AlertRuleConfig) cached;
            }
        }

        DeviceType deviceType = deviceTypeService.getById(typeId);
        AlertRuleConfig config = (deviceType != null) ? deviceType.getAlertRuleConfig() : null;

        if (config != null) {
            cacheService.set(cacheKey, config, CACHE_TTL_HOURS, TimeUnit.HOURS);
            return config;
        }

        cacheService.set(cacheKey, new CacheService.NullValue(), NULL_VALUE_TTL_MINUTES, TimeUnit.MINUTES);
        return null;
    }

    /*
     * 方法功能:使设备级告警配置缓存失效（设备配置更新后调用）
     * 参数:deviceId-设备ID
     * 返回数据:无
     *
     * 【调用时机】
     * DeviceTypeController.updateAlertConfig 或 DeviceController 更新设备配置后调用
     * 先更新DB，再删除缓存——与DictMetricServiceImpl保持一致的写策略
     * */
    @Override
    public void evictDeviceAlertConfig(Integer deviceId) {
        cacheService.delete(DEVICE_KEY_PREFIX + deviceId);
    }

    /*
     * 方法功能:使类型级告警配置缓存失效（类型配置更新后调用）
     * 参数:typeId-设备类型ID
     * 返回数据:无
     * */
    @Override
    public void evictTypeAlertConfig(Integer typeId) {
        cacheService.delete(TYPE_KEY_PREFIX + typeId);
    }
}
