package com.example.platform.ingest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.dto.IngestSampleItemDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.entity.IngestDedup;
import com.example.platform.ingest.mapper.IngestDedupMapper;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 类功能:数据接收门核心服务实现，HTTP 与 MQTT 共用的上报处理链（见接口注释）
 *
 * 【幂等事务边界】幂等键插入与采样写入同一编程式事务：采样失败时键一并回滚，
 *   网关重发不会被已回滚的键误吞；键冲突（DuplicateKeyException）视为 QoS1
 *   重发，跳过写入计入 duplicated。
 * 【解析缓存】同批内 deviceCode/pointCode 只查一次库，批内去重解析开销。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestCoreServiceImpl implements IngestCoreService {

    private final DeviceService deviceService;
    private final DevicePointBindingService devicePointBindingService;
    private final com.example.platform.ingest.service.DeviceTypeInferencer typeInferencer;
    private final PointDefinitionService pointDefinitionService;
    private final MetricSampleService metricSampleService;
    private final IngestCredentialService ingestCredentialService;
    private final IngestDedupMapper ingestDedupMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public IngestResultVO processSamples(IngestCredential credential, IngestSampleBatchDTO batch) {
        IngestResultVO result = new IngestResultVO();
        List<IngestResultVO.FailureItem> failures = new ArrayList<>();
        int accepted = 0;
        int duplicated = 0;

        Map<String, Device> deviceCache = new HashMap<>();
        Map<String, PointDefinition> pointCache = new HashMap<>();

        List<IngestSampleItemDTO> items = batch.getItems();
        for (int i = 0; i < items.size(); i++) {
            IngestSampleItemDTO item = items.get(i);
            try {
                Device device = resolveDevice(item.getDeviceCode(), item.getPointCode(), deviceCache, credential);
                PointDefinition point = resolvePoint(item.getPointCode(), pointCache);
                if (device.getCloudRoomId() == null
                        || !device.getCloudRoomId().equals(credential.getCloudRoomId())) {
                    throw new BusinessException(ResultCode.INGEST_ROOM_ACCESS_DENIED,
                            "设备不属于凭证绑定机房: " + item.getDeviceCode());
                }

                MetricSampleDTO sample = new MetricSampleDTO();
                sample.setDeviceId(device.getId());
                sample.setPointId(point.getId());
                sample.setSampleValue(item.getValue());
                sample.setSampleTime(item.getSampleTime());

                boolean isDuplicate = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                        saveIfNotDuplicated(credential, item, sample)));
                if (isDuplicate) {
                    duplicated++;
                } else {
                    accepted++;
                }
            } catch (BusinessException ex) {
                failures.add(failureOf(i, ex.getCode(), ex.getMessage()));
                log.warn("网关上报第{}条处理失败 device={} point={} error={}",
                        i, item.getDeviceCode(), item.getPointCode(), ex.getMessage());
            } catch (Exception ex) {
                failures.add(failureOf(i, ResultCode.INTERNAL_SERVER_ERROR, "写入异常: " + ex.getMessage()));
                log.error("网关上报第{}条处理异常 device={} point={}",
                        i, item.getDeviceCode(), item.getPointCode(), ex);
            }
        }

        result.setAccepted(accepted);
        result.setDuplicated(duplicated);
        result.setFailed(failures.size());
        result.setFailures(failures);
        // 接入视角：批级一次回写"最后上报所经网关"（含自动注册档案未写的场景）
        if (accepted > 0 || duplicated > 0) {
            deviceService.markIngestSource(
                    deviceCache.values().stream().map(Device::getId).toList(), credential.getId());
        }
        ingestCredentialService.refreshLastSeen(credential.getId());
        log.info("网关上报处理完成 accessKey={} accepted={} duplicated={} failed={}",
                credential.getAccessKey(), accepted, duplicated, failures.size());
        return result;
    }

    @Override
    public void heartbeat(IngestCredential credential) {
        ingestCredentialService.refreshLastSeen(credential.getId());
        log.debug("网关心跳 accessKey={}", credential.getAccessKey());
    }

    // 幂等键占位与采样写入同一事务内执行；键冲突返回 true 表示 QoS1 重发
    private boolean saveIfNotDuplicated(IngestCredential credential,
                                        IngestSampleItemDTO item, MetricSampleDTO sample) {
        try {
            IngestDedup dedup = new IngestDedup();
            dedup.setDedupKey(GatewaySignUtils.dedupKey(
                    item.getDeviceCode(), item.getPointCode(), item.getSeq()));
            ingestDedupMapper.insert(dedup);
        } catch (DuplicateKeyException ex) {
            return true;
        }
        metricSampleService.saveSample(sample, credential.getId(), true);
        return false;
    }

    private Device resolveDevice(String deviceCode, String pointCode, Map<String, Device> cache,
                                 IngestCredential credential) {
        Device cached = cache.get(deviceCode);
        if (cached != null) {
            return cached;
        }
        Device device = deviceService.findByCode(deviceCode);
        if (device == null) {
            device = tryAutoRegister(deviceCode, pointCode, credential);
        }
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceCode);
        }
        cache.put(deviceCode, device);
        return device;
    }

    // 首包自动注册：凭证开启即走，类型取 点位推断 > 凭证默认；失败降级 DEVICE_NOT_FOUND
    private Device tryAutoRegister(String deviceCode, String pointCode, IngestCredential credential) {
        if (credential.getAutoRegister() == null || credential.getAutoRegister() != 1) {
            return null;
        }
        try {
            Integer typeId = typeInferencer.inferByPointCodes(List.of(pointCode));
            if (typeId == null) {
                typeId = credential.getDefaultTypeId();
            }
            if (typeId == null) {
                return null;
            }
            Device device = deviceService.autoRegisterDevice(
                    deviceCode, credential.getCloudRoomId(), typeId, credential.getId());
            // 模板点位绑定：自动注册设备开箱即可收数（currentUserId 传凭证ID 供审计）
            devicePointBindingService.initBindingsFromTemplate(
                    device.getId(), typeId, credential.getId());
            log.info("首包自动注册 deviceCode={} roomId={} typeId={} credentialId={}",
                    deviceCode, credential.getCloudRoomId(), typeId, credential.getId());
            return device;
        } catch (Exception ex) {
            log.warn("首包自动注册失败 deviceCode={} error={}", deviceCode, ex.getMessage());
            return null;
        }
    }

    private PointDefinition resolvePoint(String pointCode, Map<String, PointDefinition> cache) {
        PointDefinition cached = cache.get(pointCode);
        if (cached != null) {
            return cached;
        }
        PointDefinition point = pointDefinitionService.getOne(new LambdaQueryWrapper<PointDefinition>()
                .eq(PointDefinition::getPointCode, pointCode));
        if (point == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "点位不存在: " + pointCode);
        }
        cache.put(pointCode, point);
        return point;
    }

    private IngestResultVO.FailureItem failureOf(int index, int errorCode, String message) {
        IngestResultVO.FailureItem failure = new IngestResultVO.FailureItem();
        failure.setIndex(index);
        failure.setErrorCode(errorCode);
        failure.setMessage(message);
        return failure;
    }
}
