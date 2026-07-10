package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.MetricSampleMapper;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.service.MetricLatestService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:指标采样历史服务实现类
 *
 * 【写入流程（单条/批量）】
 *   1. deviceScopeHelper.ensureDeviceAccess：用户对设备有授权
 *   2. pointDefinitionService.getPointDefinitionOrThrow：点位存在
 *   3. 校验采样时间 ≠ null（为空取当前）
 *   4. sample_time 不得晚于当前时间（预测数据由别的端点处理）
 *   5. 写 metric_sample
 *   6. 同步 upsert metric_latest（只取较新 sample_time 的值）
 *
 * 【查询流程】
 *   1. 指定 deviceId：必须经过设备范围校验
 *   2. 时间范围 startTime/endTime：均不为空时校验跨度 ≤ 保留策略上限天数（≤90）
 *   3. 拼接 pointId/name/unit 等 VO 字段
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricSampleServiceImpl extends ServiceImpl<MetricSampleMapper, MetricSample>
        implements MetricSampleService {

    private final DeviceScopeHelper deviceScopeHelper;
    private final DevicePointBindingService devicePointBindingService;
    private final PointDefinitionService pointDefinitionService;
    private final MetricLatestService metricLatestService;

    private static final long SAMPLE_TIME_FUTURE_TOLERANCE_SECONDS = 5; // 客户端时钟漂移容忍

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetricSample saveSample(MetricSampleDTO dto, Long currentUserId) {
        deviceScopeHelper.ensureDeviceAccess(dto.getDeviceId(), currentUserId);
        PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(dto.getPointId());
        devicePointBindingService.getActiveBindingOrThrow(
                dto.getDeviceId(), dto.getPointId(), currentUserId);
        validateSampleValue(point, dto.getSampleValue());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sampleTime = dto.getSampleTime() != null ? dto.getSampleTime() : now;
        if (sampleTime.isAfter(now.plusSeconds(SAMPLE_TIME_FUTURE_TOLERANCE_SECONDS))) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "采样时间不能晚于服务端当前时间");
        }

        MetricSample sample = new MetricSample();
        sample.setDeviceId(dto.getDeviceId());
        sample.setPointId(dto.getPointId());
        sample.setSampleValue(dto.getSampleValue());
        sample.setSampleTime(sampleTime);
        if (!save(sample)) {
            throw new BusinessException("写入指标采样失败");
        }
        // key:  新值写入 metric_latest
        metricLatestService.upsertLatest(dto.getDeviceId(), dto.getPointId(),
                dto.getSampleValue(), sampleTime);
        log.info("指标采样写入 deviceId={} pointId={} value={} time={}",
                dto.getDeviceId(), point.getPointCode(), dto.getSampleValue(), sampleTime);
        return sample;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MetricSampleVO> saveBatch(MetricSampleBatchDTO batchDTO, Long currentUserId) {
        List<MetricSampleVO> result = new ArrayList<>(batchDTO.getItems().size());
        for (MetricSampleDTO item : batchDTO.getItems()) {
            MetricSample saved = saveSample(item, currentUserId);
            result.add(toVO(saved, item));
        }
        return result;
    }

    @Override
    public Page<MetricSampleVO> pageSamples(Long currentUserId, Integer current, Integer size,
                                            Integer deviceId, Long pointId,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        int[] params = PaginationUtils.validate(current, size);
        Page<MetricSample> samplePage = new Page<>(params[0], params[1]);

        if (deviceId == null) {
            // 不允许无 deviceId 的跨库聚合扫描，必须指定设备
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "指标采样查询必须指定 deviceId");
        }
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);

        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            if (duration.isNegative()) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "查询起始时间不能晚于结束时间");
            }
            if (duration.compareTo(Duration.ofDays(DevicePointMetricContract.MAX_RETENTION_DAYS)) > 0) {
                throw new BusinessException(ResultCode.DATA_TIME_RANGE_EXCEED,
                        "查询时间跨度不能超过 " + DevicePointMetricContract.MAX_RETENTION_DAYS + " 天");
            }
        } else if (startTime == null && endTime == null) {
            startTime = LocalDateTime.now().minusDays(DevicePointMetricContract.MAX_RETENTION_DAYS);
        }

        LambdaQueryWrapper<MetricSample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricSample::getDeviceId, deviceId);
        if (pointId != null) {
            wrapper.eq(MetricSample::getPointId, pointId);
        }
        if (startTime != null) {
            wrapper.ge(MetricSample::getSampleTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MetricSample::getSampleTime, endTime);
        }
        wrapper.orderByDesc(MetricSample::getSampleTime);

        Page<MetricSample> resultPage = page(samplePage, wrapper);
        return mapToVOPage(resultPage, params[0], params[1]);
    }

    private Page<MetricSampleVO> mapToVOPage(Page<MetricSample> source, int current, int size) {
        Page<MetricSampleVO> result = new Page<>(current, size);
        result.setTotal(source.getTotal());
        List<MetricSampleVO> records = new ArrayList<>();
        for (MetricSample sample : source.getRecords()) {
            MetricSampleVO vo = new MetricSampleVO();
            vo.setId(sample.getId());
            vo.setDeviceId(sample.getDeviceId());
            vo.setPointId(sample.getPointId());
            PointDefinition point = pointDefinitionService.getById(sample.getPointId());
            if (point != null) {
                vo.setPointCode(point.getPointCode());
                vo.setPointName(point.getPointName());
                vo.setUnit(point.getUnit());
            }
            vo.setSampleValue(sample.getSampleValue());
            vo.setSampleTime(sample.getSampleTime());
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }

    private MetricSampleVO toVO(MetricSample sample, MetricSampleDTO sourceDTO) {
        MetricSampleVO vo = new MetricSampleVO();
        vo.setId(sample.getId());
        vo.setDeviceId(sample.getDeviceId());
        vo.setPointId(sample.getPointId());
        PointDefinition point = pointDefinitionService.getById(sample.getPointId());
        if (point != null) {
            vo.setPointCode(point.getPointCode());
            vo.setPointName(point.getPointName());
            vo.setUnit(point.getUnit());
        }
        vo.setSampleValue(sample.getSampleValue());
        vo.setSampleTime(sample.getSampleTime());
        return vo;
    }

    private void validateSampleValue(PointDefinition point, BigDecimal sampleValue) {
        String dataType = point.getDataType();
        if ("STRING".equals(dataType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "STRING 点位不能写入数值型指标采样表");
        }
        if ("BOOLEAN".equals(dataType)
                && sampleValue.compareTo(BigDecimal.ZERO) != 0
                && sampleValue.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(ResultCode.DATA_VALUE_OUT_OF_RANGE,
                    "BOOLEAN 点位采样值仅允许 0 或 1");
        }
    }
}
