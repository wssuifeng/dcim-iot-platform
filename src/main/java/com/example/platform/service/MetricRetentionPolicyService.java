package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.MetricRetentionPolicyDTO;
import com.example.platform.entity.MetricRetentionPolicy;

import java.util.List;

/*
 * 类功能:指标保留策略服务接口
 *
 * 【角色与范围】PLATFORM_ADMIN 维护全局（pointId=null）和单点保留策略
 * <p>保留天数上限 90（见 DevicePointMetricContract.MAX_RETENTION_DAYS），由 DTO 和服务双重校验</p>
 * */
public interface MetricRetentionPolicyService extends IService<MetricRetentionPolicy> {

    MetricRetentionPolicy upsertPolicy(MetricRetentionPolicyDTO dto);

    MetricRetentionPolicy getGlobalPolicyOrThrow();

    MetricRetentionPolicy findByPointId(Long pointId);

    /*
     * 方法功能:解析某点位的有效保留天数（优先点位策略，回退全局默认 90 天）
     * */
    int resolveEffectiveRetentionDays(Long pointId);

    /*
     * 方法功能:列出全部保留策略（含全局和单点），PLATFORM_ADMIN 维护参考
     * */
    List<MetricRetentionPolicy> listAllPolicies();
}
