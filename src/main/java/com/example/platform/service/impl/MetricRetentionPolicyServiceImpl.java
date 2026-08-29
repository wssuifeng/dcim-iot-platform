package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.MetricRetentionPolicyDTO;
import com.example.platform.entity.MetricRetentionPolicy;
import com.example.platform.mapper.MetricRetentionPolicyMapper;
import com.example.platform.service.MetricRetentionPolicyService;
import com.example.platform.service.DevicePointMetricContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * 类功能:指标保留策略服务实现类
 *
 * 【upsert 策略】
 *   - pointId 为 null（全局默认）：按 pointId IS NULL 唯一记录 upsert；天数上限 90 天
 *   - pointId 非 null（单点策略）：校验指标存在；按 pointId 唯一键 upsert
 *
 * 【解析有效保留天数】
 *   - 优先取单点策略的 retentionDays
 *   - 否则回退到全局默认（也是 90 天）
 * */
@Slf4j
@Service
public class MetricRetentionPolicyServiceImpl
        extends ServiceImpl<MetricRetentionPolicyMapper, MetricRetentionPolicy>
        implements MetricRetentionPolicyService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetricRetentionPolicy upsertPolicy(MetricRetentionPolicyDTO dto) {
        validateRetentionDays(dto.getRetentionDays());

        if (dto.getPointId() == null) {
            // 全局策略：按 pointId IS NULL 唯一
            MetricRetentionPolicy existing = findGlobalPolicy();
            if (existing != null) {
                existing.setRetentionDays(dto.getRetentionDays());
                updateById(existing);
                log.info("全局指标保留策略更新为 {} 天", dto.getRetentionDays());
                return existing;
            }
            MetricRetentionPolicy global = new MetricRetentionPolicy();
            global.setPointId(null);
            global.setRetentionDays(dto.getRetentionDays());
            save(global);
            log.info("全局指标保留策略创建为 {} 天", dto.getRetentionDays());
            return global;
        }

        // 单点策略：按 pointId 唯一 upsert（不强制指标存在，允许指标先于策略删除后回收）
        MetricRetentionPolicy existing = findByPointId(dto.getPointId());
        if (existing != null) {
            existing.setRetentionDays(dto.getRetentionDays());
            updateById(existing);
            log.info("指标 {} 指标保留策略更新为 {} 天", dto.getPointId(), dto.getRetentionDays());
            return existing;
        }
        MetricRetentionPolicy policy = new MetricRetentionPolicy();
        policy.setPointId(dto.getPointId());
        policy.setRetentionDays(dto.getRetentionDays());
        save(policy);
        log.info("指标 {} 指标保留策略创建为 {} 天", dto.getPointId(), dto.getRetentionDays());
        return policy;
    }

    @Override
    public MetricRetentionPolicy getGlobalPolicyOrThrow() {
        MetricRetentionPolicy global = findGlobalPolicy();
        if (global == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "尚未配置全局指标保留策略");
        }
        return global;
    }

    @Override
    public MetricRetentionPolicy findByPointId(Long pointId) {
        if (pointId == null) {
            return findGlobalPolicy();
        }
        LambdaQueryWrapper<MetricRetentionPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricRetentionPolicy::getPointId, pointId);
        return getOne(wrapper);
    }

    @Override
    public int resolveEffectiveRetentionDays(Long pointId) {
        if (pointId != null) {
            MetricRetentionPolicy pointPolicy = findByPointId(pointId);
            if (pointPolicy != null && pointPolicy.getRetentionDays() != null) {
                return Math.min(pointPolicy.getRetentionDays(), DevicePointMetricContract.MAX_RETENTION_DAYS);
            }
        }
        MetricRetentionPolicy global = findGlobalPolicy();
        if (global != null && global.getRetentionDays() != null) {
            return Math.min(global.getRetentionDays(), DevicePointMetricContract.MAX_RETENTION_DAYS);
        }
        return DevicePointMetricContract.DEFAULT_RETENTION_DAYS;
    }

    @Override
    public List<MetricRetentionPolicy> listAllPolicies() {
        LambdaQueryWrapper<MetricRetentionPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(MetricRetentionPolicy::getPointId);
        return list(wrapper);
    }

    private MetricRetentionPolicy findGlobalPolicy() {
        LambdaQueryWrapper<MetricRetentionPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(MetricRetentionPolicy::getPointId);
        return getOne(wrapper, false);
    }

    private void validateRetentionDays(Integer retentionDays) {
        if (retentionDays == null || retentionDays < 1
                || retentionDays > DevicePointMetricContract.MAX_RETENTION_DAYS) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "保留天数应在 1~" + DevicePointMetricContract.MAX_RETENTION_DAYS + " 之间");
        }
    }
}
