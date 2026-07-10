package com.example.platform.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DeviceTypeService;
import com.example.platform.service.OrganizationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService 数据范围单元测试")
class DeviceScopedServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceTypeService deviceTypeService;
    @Mock private AlertConfigService alertConfigService;
    @Mock private DataScopeService dataScopeService;
    @Mock private OrganizationService organizationService;
    @Mock private CloudRoomService cloudRoomService;

    @InjectMocks private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() throws Exception {
        Field field = null;
        Class<?> type = DeviceServiceImpl.class;
        while (type != null && field == null) {
            try { field = type.getDeclaredField("baseMapper"); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        assertThat(field).isNotNull();
        field.setAccessible(true);
        field.set(deviceService, deviceMapper);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(DeviceMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Device.class);
    }

    @Test
    @DisplayName("创建设备必须同时挂靠组织和机房")
    void createRequiresOrganizationAndRoom() {
        Device device = new Device();
        device.setTypeId(1);

        assertThatThrownBy(() -> deviceService.createScoped(device, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须挂靠组织和机房");
        verify(deviceMapper, never()).insert((Device) any());
    }

    @Test
    @DisplayName("机房不属于设备组织时拒绝创建")
    void createRejectsOrganizationRoomMismatch() {
        Device device = validDevice(1L, 20L);
        Organization organization = organization(1L, 0);
        CloudRoom room = room(20L, 2L, 0);
        when(organizationService.getById(1L)).thenReturn(organization);
        when(cloudRoomService.getById(20L)).thenReturn(room);

        assertThatThrownBy(() -> deviceService.createScoped(device, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("归属不一致");
    }

    @Test
    @DisplayName("ROOMS 用户不能访问同组织其他机房设备")
    void roomsScopeRejectsOtherRoomDevice() {
        Device device = validDevice(1L, 20L);
        device.setId(1);
        when(deviceMapper.selectById(1)).thenReturn(device);
        when(dataScopeService.resolveDataScope(9L)).thenReturn(scope(
                "ROOMS", Set.of(1L), Set.of(21L)));

        assertThatThrownBy(() -> deviceService.getScoped(1, 9L))
                .isInstanceOf(DataScopeViolationException.class);
    }

    @Test
    @DisplayName("旧版 Excel 缺少组织机房字段时整行失败且不写设备")
    void legacyExcelIsRejected() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LegacyDeviceRow row = new LegacyDeviceRow();
        row.deviceName = "旧设备";
        row.deviceCode = "LEGACY-001";
        row.typeCode = "UPS";
        EasyExcel.write(output, LegacyDeviceRow.class).sheet("设备").doWrite(List.of(row));
        MockMultipartFile file = new MockMultipartFile(
                "file", "legacy.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                output.toByteArray());

        var result = deviceService.importFromExcelScoped(file, 8L);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailDetails().get(0).getReason()).contains("旧版 Excel 模板不再兼容");
        verify(deviceMapper, never()).insert((Device) any());
    }

    private Device validDevice(Long organizationId, Long roomId) {
        Device device = new Device();
        device.setDeviceName("UPS-01");
        device.setDeviceCode("UPS-01");
        device.setTypeId(1);
        device.setOrganizationId(organizationId);
        device.setCloudRoomId(roomId);
        return device;
    }

    private Organization organization(Long id, int status) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setStatus(status);
        return organization;
    }

    private CloudRoom room(Long id, Long organizationId, int status) {
        CloudRoom room = new CloudRoom();
        room.setId(id);
        room.setOrganizationId(organizationId);
        room.setStatus(status);
        return room;
    }

    private DataScopeContext scope(String scopeType, Set<Long> orgIds, Set<Long> roomIds) {
        return DataScopeContext.builder()
                .userId(9L).role("OPERATOR").scopeType(scopeType)
                .organizationIds(orgIds).cloudRoomIds(roomIds).build();
    }

    @lombok.Data
    static class LegacyDeviceRow {
        @com.alibaba.excel.annotation.ExcelProperty("设备名称") String deviceName;
        @com.alibaba.excel.annotation.ExcelProperty("设备编码") String deviceCode;
        @com.alibaba.excel.annotation.ExcelProperty("类型编码") String typeCode;
        @com.alibaba.excel.annotation.ExcelProperty("位置") String location;
        @com.alibaba.excel.annotation.ExcelProperty("IP地址") String ipAddress;
    }
}
