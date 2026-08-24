package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleBatchResultVO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleExportRow;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.MetricSampleSavedEvent;
import com.example.platform.mapper.MetricSampleMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.MetricLatestService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * 类功能:指标采样历史服务实现类（T1 采样事务唯一所有者）
 *
 * 【T1 写入流程（单条）】
 *   1. deviceScopeHelper.ensureDeviceAccess：用户对设备有授权
 *   2. pointDefinitionService.getPointDefinitionOrThrow：点位存在 + 数据类型校验
 *   3. devicePointBindingService.getActiveBindingOrThrow：设备点位已启用绑定
 *   4. 双时间基准（D09）：sample_time 不得晚于 服务端当前时间+5分钟（设备时钟容忍）
 *   5. INSERT metric_sample（receive_time 由数据库默认值生成）
 *   6. UPSERT metric_latest（只取较新 sample_time 的值）
 *   7. 刷设备在线状态：last_online_time=now、status=1（D20 状态闭环）
 *   8. 事务提交后条件发布 MetricSampleSavedEvent（AFTER_COMMIT，仅向前传播）
 *
 * 【批量（D10）】saveBatch 无整体事务，循环 TransactionTemplate 逐条独立提交；
 *   任一条失败仅记入 failures，不影响其余条目。
 *
 * 【依赖单向铁律】T1 内不调用任何告警类；评估由事件监听方完成。
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
    private final DeviceService deviceService;
    private final DataScopeService dataScopeService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    // 客户端时钟漂移容忍：sample_time 晚于服务端时间该秒数内可接受（D09）
    private static final long SAMPLE_TIME_FUTURE_TOLERANCE_SECONDS = 5 * 60;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetricSample saveSample(MetricSampleDTO dto, Long currentUserId, boolean triggerAlarm) {
        return doSaveSample(dto, currentUserId, triggerAlarm);
    }

    @Override
    public MetricSampleBatchResultVO saveBatch(MetricSampleBatchDTO batchDTO, Long currentUserId,
                                               boolean triggerAlarm) {
        MetricSampleBatchResultVO result = new MetricSampleBatchResultVO();
        List<MetricSampleBatchResultVO.FailureItem> failures = new ArrayList<>();
        int successCount = 0;

        List<MetricSampleDTO> items = batchDTO.getItems();
        for (int i = 0; i < items.size(); i++) {
            MetricSampleDTO item = items.get(i);
            try {
                // 逐条独立事务（D10）：编程式事务规避自调用代理失效，任一失败不回滚其余
                MetricSample saved = transactionTemplate.execute(status ->
                        doSaveSample(item, currentUserId, triggerAlarm));
                if (saved != null) {
                    successCount++;
                }
            } catch (BusinessException ex) {
                MetricSampleBatchResultVO.FailureItem failure = new MetricSampleBatchResultVO.FailureItem();
                failure.setIndex(i);
                failure.setErrorCode(ex.getCode());
                failure.setMessage(ex.getMessage());
                failures.add(failure);
                log.warn("批量采样第{}条写入失败 device={} point={} error={}",
                        i, item.getDeviceId(), item.getPointId(), ex.getMessage());
            } catch (Exception ex) {
                MetricSampleBatchResultVO.FailureItem failure = new MetricSampleBatchResultVO.FailureItem();
                failure.setIndex(i);
                failure.setErrorCode(ResultCode.INTERNAL_SERVER_ERROR);
                failure.setMessage("写入异常: " + ex.getMessage());
                failures.add(failure);
                log.error("批量采样第{}条写入异常", i, ex);
            }
        }

        result.setSuccessCount(successCount);
        result.setFailures(failures);
        log.info("批量采样写入完成 success={} failure={}", successCount, failures.size());
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

    // ===================== 内部辅助 =====================

    private static final int EXPORT_MAX_ROWS = 10000;

    @Override
    public List<MetricSampleExportRow> exportRows(Long currentUserId, Integer deviceId, Long pointId,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
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
        wrapper.orderByDesc(MetricSample::getSampleTime)
                .last("LIMIT " + EXPORT_MAX_ROWS);
        List<MetricSample> samples = list(wrapper);

        Map<Long, PointDefinition> pointById = samples.isEmpty() ? Map.of()
                : pointDefinitionService.listByIds(samples.stream()
                        .map(MetricSample::getPointId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(
                                PointDefinition::getId, p -> p));
        List<MetricSampleExportRow> rows = new ArrayList<>(samples.size());
        for (MetricSample sample : samples) {
            MetricSampleExportRow row = new MetricSampleExportRow();
            row.setId(sample.getId());
            row.setDeviceId(sample.getDeviceId());
            PointDefinition point = pointById.get(sample.getPointId());
            if (point != null) {
                row.setPointCode(point.getPointCode());
                row.setPointName(point.getPointName());
            }
            row.setSampleValue(sample.getSampleValue());
            row.setSampleTime(sample.getSampleTime());
            row.setReceiveTime(sample.getReceiveTime());
            rows.add(row);
        }
        return rows;
    }

    private MetricSample doSaveSample(MetricSampleDTO dto, Long currentUserId, boolean triggerAlarm) {
        deviceScopeHelper.ensureDeviceAccess(dto.getDeviceId(), currentUserId);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.hasWritePermission()) {
            throw new DataScopeViolationException("MetricSample", "read-only-scope");
        }
        PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(dto.getPointId());
        devicePointBindingService.getActiveBindingOrThrow(
                dto.getDeviceId(), dto.getPointId(), currentUserId);
        validateSampleValue(point, dto.getSampleValue());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sampleTime = dto.getSampleTime() != null ? dto.getSampleTime() : now;
        if (sampleTime.isAfter(now.plusSeconds(SAMPLE_TIME_FUTURE_TOLERANCE_SECONDS))) {
            throw new BusinessException(ResultCode.DATA_TIME_RANGE_EXCEED,
                    "采样时间不能晚于平台接收时间+5分钟（设备时钟偏差容忍）");
        }

        MetricSample sample = new MetricSample();
        sample.setDeviceId(dto.getDeviceId());
        sample.setPointId(dto.getPointId());
        sample.setSampleValue(dto.getSampleValue());
        sample.setSampleTime(sampleTime);
        if (!save(sample)) {
            throw new BusinessException("写入指标采样失败");
        }
        metricLatestService.upsertLatest(dto.getDeviceId(), dto.getPointId(),
                dto.getSampleValue(), sampleTime);
        refreshDeviceOnline(dto.getDeviceId(), now);
        if (triggerAlarm) {
            // AFTER_COMMIT 监听：T1 提交后评估才会执行（事件仅向前传播）
            eventPublisher.publishEvent(new MetricSampleSavedEvent(
                    dto.getDeviceId(), dto.getPointId(), dto.getSampleValue(), sampleTime));
        }
        log.info("指标采样写入 deviceId={} pointId={} value={} time={} triggerAlarm={}",
                dto.getDeviceId(), point.getPointCode(), dto.getSampleValue(), sampleTime, triggerAlarm);
        return sample;
    }

    private void refreshDeviceOnline(Integer deviceId, LocalDateTime now) {
        // D20 状态闭环：收到上报即设备在线（离线检测任务负责置0，形成闭环）
        deviceService.update(new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, deviceId)
                .set(Device::getLastOnlineTime, now)
                .set(Device::getStatus, 1));
    }

    private Page<MetricSampleVO> mapToVOPage(Page<MetricSample> source, int current, int size) {
        Page<MetricSampleVO> result = new Page<>(current, size);
        result.setTotal(source.getTotal());
        List<MetricSampleVO> records = new ArrayList<>();
        for (MetricSample sample : source.getRecords()) {
            records.add(toVO(sample));
        }
        result.setRecords(records);
        return result;
    }

    private MetricSampleVO toVO(MetricSample sample) {
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
