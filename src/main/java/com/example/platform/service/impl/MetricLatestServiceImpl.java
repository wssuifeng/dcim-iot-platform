package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.platform.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.MetricLatestVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.MetricLatest;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.MetricLatestMapper;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.MetricLatestService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:指标最新值服务实现类，提供最新值快照的 upsert 与按设备范围查询
 *
 * 【upsert 逻辑】
 *   - 数据库中已有记录且新 sampleTime >= latest_time → UPDATE latest_value/latest_time
 *   - 数据库中已有记录且新 sampleTime < latest_time → 跳过（保持最新值快照较新）
 *   - 无记录 → INSERT
 *   - 注意：metric_latest 无逻辑删除字段（无 deleted），按唯一键 (device_id, point_id) 管控
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricLatestServiceImpl extends ServiceImpl<MetricLatestMapper, MetricLatest>
        implements MetricLatestService {

    private final DeviceScopeHelper deviceScopeHelper;
    private final PointDefinitionService pointDefinitionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetricLatest upsertLatest(Integer deviceId, Long pointId,
                                     BigDecimal value, LocalDateTime sampleTime) {
        LambdaQueryWrapper<MetricLatest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricLatest::getDeviceId, deviceId)
                .eq(MetricLatest::getPointId, pointId);
        MetricLatest existing = getOne(wrapper);

        if (existing == null) {
            MetricLatest latest = new MetricLatest();
            latest.setDeviceId(deviceId);
            latest.setPointId(pointId);
            latest.setLatestValue(value);
            latest.setLatestTime(sampleTime);
            save(latest);
            return latest;
        }

        LocalDateTime existingTime = existing.getLatestTime();
        if (existingTime != null && sampleTime.isBefore(existingTime)) {
            log.debug("采样时间 {} 早于已存在的最新值时间 {}，跳过更新 (device={}, point={})",
                    sampleTime, existingTime, deviceId, pointId);
            return existing;
        }

        LambdaUpdateWrapper<MetricLatest> update = new LambdaUpdateWrapper<>();
        update.eq(MetricLatest::getId, existing.getId())
                .set(MetricLatest::getLatestValue, value)
                .set(MetricLatest::getLatestTime, sampleTime);
        update(update);
        existing.setLatestValue(value);
        existing.setLatestTime(sampleTime);
        return existing;
    }

    @Override
    public List<MetricLatestVO> listByDevice(Integer deviceId, Long currentUserId) {
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);

        LambdaQueryWrapper<MetricLatest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricLatest::getDeviceId, deviceId)
                .orderByAsc(MetricLatest::getPointId);
        List<MetricLatest> latestList = list(wrapper);

        List<MetricLatestVO> result = new ArrayList<>(latestList.size());
        for (MetricLatest latest : latestList) {
            result.add(toVO(latest));
        }
        return result;
    }

    @Override
    public Page<MetricLatestVO> pageLatest(Long currentUserId, Integer current, Integer size,
                                          Integer deviceId, Long pointId) {
        int[] params = PaginationUtils.validate(current, size);
        Page<MetricLatest> source = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<MetricLatest> wrapper = new LambdaQueryWrapper<>();

        if (deviceId == null) {
            // 不允许无 deviceId 跨设备扫描最新值（服务层数据范围无法识别具体设备归属）
            // 看板大类聚合由 TRAE Agent 后续在 BIZ-TRAE-02 看板模块实现
            throw new BusinessException("指标最新值查询必须指定 deviceId");
        }
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        wrapper.eq(MetricLatest::getDeviceId, deviceId);

        if (pointId != null) {
            wrapper.eq(MetricLatest::getPointId, pointId);
        }
        wrapper.orderByAsc(MetricLatest::getPointId);

        Page<MetricLatest> resultPage = page(source, wrapper);
        Page<MetricLatestVO> result = new Page<>(params[0], params[1]);
        result.setTotal(resultPage.getTotal());
        List<MetricLatestVO> records = new ArrayList<>();
        for (MetricLatest latest : resultPage.getRecords()) {
            records.add(toVO(latest));
        }
        result.setRecords(records);
        return result;
    }

    private MetricLatestVO toVO(MetricLatest latest) {
        MetricLatestVO vo = new MetricLatestVO();
        vo.setId(latest.getId());
        vo.setDeviceId(latest.getDeviceId());
        vo.setPointId(latest.getPointId());
        PointDefinition point = pointDefinitionService.getById(latest.getPointId());
        if (point != null) {
            vo.setPointCode(point.getPointCode());
            vo.setPointName(point.getPointName());
            vo.setUnit(point.getUnit());
        }
        vo.setLatestValue(latest.getLatestValue());
        vo.setLatestTime(latest.getLatestTime());
        return vo;
    }
}