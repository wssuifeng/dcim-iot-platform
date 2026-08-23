package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTypeServiceImplTest {

    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private DeviceService deviceService;

    private DeviceTypeServiceImpl deviceTypeService;

    @BeforeEach
    void setUp() {
        deviceTypeService = new DeviceTypeServiceImpl(deviceService);
        ReflectionTestUtils.setField(deviceTypeService, "baseMapper", deviceTypeMapper);
    }

    @Test
    void deleteMustReturnBusinessErrorWhenDevicesAreStillAssociated() {
        DeviceType type = new DeviceType();
        type.setId(6);
        type.setTypeCode("LEGACY_TYPE");
        when(deviceTypeMapper.selectById(6)).thenReturn(type);
        when(deviceService.findByTypeId(6)).thenReturn(List.of(new Device()));

        assertThatThrownBy(() -> deviceTypeService.deleteById(6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先删除或迁移关联设备")
                .extracting("code")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(deviceTypeMapper, never()).deleteById(6);
    }

    @Test
    void frozenProductTypeMustRemainProtected() {
        DeviceType type = new DeviceType();
        type.setId(1);
        type.setTypeCode("UPS");
        when(deviceTypeMapper.selectById(1)).thenReturn(type);

        assertThatThrownBy(() -> deviceTypeService.deleteById(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冻结基础类型");

        verify(deviceService, never()).findByTypeId(1);
        verify(deviceTypeMapper, never()).deleteById(1);
    }
}
