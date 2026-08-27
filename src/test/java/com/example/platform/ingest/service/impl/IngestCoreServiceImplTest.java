package com.example.platform.ingest.service.impl;

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
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestCoreServiceImplTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private MetricSampleService metricSampleService;
    @Mock
    private IngestCredentialService ingestCredentialService;
    @Mock
    private IngestDedupMapper ingestDedupMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private org.springframework.transaction.TransactionStatus transactionStatus;

    @InjectMocks
    private IngestCoreServiceImpl coreService;

    @Test
    void validItemsAcceptedAndDelegateToSamplePipeline() {
        stubTransactionPassThrough();
        stubDeviceAndPoint("DEV-1", 11, "temperature", 1L);
        IngestCredential credential = credential(100L, 5L);

        IngestResultVO result = coreService.processSamples(credential, batch(item("DEV-1", "temperature", 22.5, 1L)));

        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getDuplicated()).isZero();
        assertThat(result.getFailed()).isZero();
        verify(metricSampleService).saveSample(any(MetricSampleDTO.class), eq(100L), eq(true));
        verify(ingestCredentialService).refreshLastSeen(100L);
    }

    @Test
    void crossRoomDeviceRejected() {
        // 机房归属校验先于幂等事务，无需桩事务模板
        stubDeviceAndPoint("DEV-1", 11, "temperature", 1L);
        // 凭证绑机房 5，设备在机房 6
        IngestCredential credential = credential(100L, 6L);

        IngestResultVO result = coreService.processSamples(credential, batch(item("DEV-1", "temperature", 22.5, 1L)));

        assertThat(result.getAccepted()).isZero();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getErrorCode())
                .isEqualTo(ResultCode.INGEST_ROOM_ACCESS_DENIED);
        verify(metricSampleService, org.mockito.Mockito.never())
                .saveSample(any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void unknownDeviceRejectedWithoutBlockingOthers() {
        stubTransactionPassThrough();
        when(deviceService.getOne(any(Wrapper.class))).thenReturn(device("DEV-1", 11, 5L), (Device) null);
        stubPoint("temperature", 1L);
        IngestCredential credential = credential(100L, 5L);

        IngestResultVO result = coreService.processSamples(credential, batch(
                item("DEV-1", "temperature", 22.5, 1L),
                item("DEV-X", "temperature", 23.0, 2L)));

        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getErrorCode()).isEqualTo(ResultCode.DEVICE_NOT_FOUND);
    }

    @Test
    void duplicateDedupKeyCountedAsDuplicated() {
        stubTransactionPassThrough();
        stubDeviceAndPoint("DEV-1", 11, "temperature", 1L);
        when(ingestDedupMapper.insert(any(IngestDedup.class)))
                .thenThrow(new DuplicateKeyException("uk_ingest_dedup"));
        IngestCredential credential = credential(100L, 5L);

        IngestResultVO result = coreService.processSamples(credential, batch(item("DEV-1", "temperature", 22.5, 1L)));

        assertThat(result.getAccepted()).isZero();
        assertThat(result.getDuplicated()).isEqualTo(1);
        verify(metricSampleService, org.mockito.Mockito.never())
                .saveSample(any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void samplePipelineFailureRecordedAndDedupKeyRolledBackWithSameTransaction() {
        // 事务模板透传：saveIfNotDuplicated 与采样写入同事务，采样抛异常向外传播
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        stubDeviceAndPoint("DEV-1", 11, "temperature", 1L);
        when(metricSampleService.saveSample(any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ResultCode.DATA_TIME_RANGE_EXCEED, "采样时间超出容差"));
        IngestCredential credential = credential(100L, 5L);

        IngestResultVO result = coreService.processSamples(credential, batch(item("DEV-1", "temperature", 22.5, 1L)));

        assertThat(result.getAccepted()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getErrorCode()).isEqualTo(ResultCode.DATA_TIME_RANGE_EXCEED);
    }

    @Test
    void deviceAndPointResolvedOncePerBatch() {
        stubTransactionPassThrough();
        stubDeviceAndPoint("DEV-1", 11, "temperature", 1L);
        IngestCredential credential = credential(100L, 5L);

        coreService.processSamples(credential, batch(
                item("DEV-1", "temperature", 22.5, 1L),
                item("DEV-1", "temperature", 23.0, 2L),
                item("DEV-1", "temperature", 23.5, 3L)));

        verify(deviceService, org.mockito.Mockito.times(1)).getOne(any(Wrapper.class));
        verify(pointDefinitionService, org.mockito.Mockito.times(1)).getOne(any(Wrapper.class));
        verify(metricSampleService, org.mockito.Mockito.times(3))
                .saveSample(any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void heartbeatRefreshesCredentialLastSeen() {
        coreService.heartbeat(credential(100L, 5L));

        verify(ingestCredentialService).refreshLastSeen(100L);
    }

    private void stubTransactionPassThrough() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    private void stubDeviceAndPoint(String deviceCode, Integer deviceId, String pointCode, Long pointId) {
        when(deviceService.getOne(any(Wrapper.class))).thenReturn(device(deviceCode, deviceId, 5L));
        stubPoint(pointCode, pointId);
    }

    private void stubPoint(String pointCode, Long pointId) {
        PointDefinition point = new PointDefinition();
        point.setId(pointId);
        point.setPointCode(pointCode);
        when(pointDefinitionService.getOne(any(Wrapper.class))).thenReturn(point);
    }

    private Device device(String deviceCode, Integer deviceId, Long cloudRoomId) {
        Device device = new Device();
        device.setId(deviceId);
        device.setDeviceCode(deviceCode);
        device.setCloudRoomId(cloudRoomId);
        return device;
    }

    private IngestCredential credential(Long id, Long cloudRoomId) {
        IngestCredential credential = new IngestCredential();
        credential.setId(id);
        credential.setAccessKey("gw-test");
        credential.setCloudRoomId(cloudRoomId);
        return credential;
    }

    private IngestSampleBatchDTO batch(IngestSampleItemDTO... items) {
        IngestSampleBatchDTO batch = new IngestSampleBatchDTO();
        batch.setItems(List.of(items));
        return batch;
    }

    private IngestSampleItemDTO item(String deviceCode, String pointCode, double value, long seq) {
        IngestSampleItemDTO item = new IngestSampleItemDTO();
        item.setDeviceCode(deviceCode);
        item.setPointCode(pointCode);
        item.setValue(BigDecimal.valueOf(value));
        item.setSampleTime(LocalDateTime.now().withNano(0));
        item.setSeq(seq);
        return item;
    }
}
