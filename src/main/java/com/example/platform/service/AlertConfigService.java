package com.example.platform.service;

import com.example.platform.dto.AlertRuleConfig;

/*
 * 类功能:告警配置缓存服务接口，提供设备级和类型级告警规则的缓存读取与失效操作
 *
 * 【缓存策略】
 * - 设备级Key: config:alert:device:{deviceId}，TTL=1h
 * - 类型级Key: config:alert:type:{typeId}，TTL=1h
 * - 读流程: 缓存 → 数据库 → 回填（Cache-Aside模式）
 * - 写策略: 先更新DB，再删除缓存
 * - 防穿透: DB无数据时存入NullValue占位（TTL=5min）
 * */
public interface AlertConfigService {

    /*
     * 方法功能:从缓存获取设备级告警配置，未命中时查DB回填
     * 参数:deviceId-设备ID
     * 返回数据:AlertRuleConfig-设备级告警配置，无配置返回null
     * */
    AlertRuleConfig getDeviceAlertConfig(Integer deviceId);

    /*
     * 方法功能:从缓存获取类型级告警配置，未命中时查DB回填
     * 参数:typeId-设备类型ID
     * 返回数据:AlertRuleConfig-类型级告警配置，无配置返回null
     * */
    AlertRuleConfig getTypeAlertConfig(Integer typeId);

    /*
     * 方法功能:使设备级告警配置缓存失效（设备配置更新后调用）
     * 参数:deviceId-设备ID
     * 返回数据:无
     * */
    void evictDeviceAlertConfig(Integer deviceId);

    /*
     * 方法功能:使类型级告警配置缓存失效（类型配置更新后调用）
     * 参数:typeId-设备类型ID
     * 返回数据:无
     * */
    void evictTypeAlertConfig(Integer typeId);
}
