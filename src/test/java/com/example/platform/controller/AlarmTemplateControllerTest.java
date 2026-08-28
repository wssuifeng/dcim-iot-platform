package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.Device;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmTemplateController 单元测试
 * 覆盖：生效模版解析端点的三态（改绑/跟随类型/无绑定）与参数透传
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmTemplateController 单元测试")
class AlarmTemplateControllerTest {

    @Mock
    private AlarmTemplateService alarmTemplateService;
    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private AlarmTemplateController controller;

    private static final Integer DEVICE_ID = 2;

    private AlarmTemplate template(long id, String name) {
        AlarmTemplate template = new AlarmTemplate();
        template.setId(id);
        template.setTemplateName(name);
        return template;
    }

    @Test
    @DisplayName("设备生效模版：改绑时 source=DEVICE_OVERRIDE")
    void effective_shouldReportDeviceOverride() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setAlarmTemplateId(200L);
        when(deviceService.getDeviceOrThrow(DEVICE_ID)).thenReturn(device);
        when(alarmTemplateService.resolveEffectiveTemplate(device)).thenReturn(template(200L, "风口加严版"));

        Result<Map<String, Object>> result = controller.effective(DEVICE_ID);

        assertThat(result.getData())
                .containsEntry("templateId", 200L)
                .containsEntry("templateName", "风口加严版")
                .containsEntry("source", "DEVICE_OVERRIDE");
    }

    @Test
    @DisplayName("设备生效模版：跟随类型时 source=TEMPLATE")
    void effective_shouldReportTypeTemplate() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setAlarmTemplateId(null);
        when(deviceService.getDeviceOrThrow(DEVICE_ID)).thenReturn(device);
        when(alarmTemplateService.resolveEffectiveTemplate(device)).thenReturn(template(100L, "温湿度标准监控"));

        Result<Map<String, Object>> result = controller.effective(DEVICE_ID);

        assertThat(result.getData())
                .containsEntry("templateId", 100L)
                .containsEntry("source", "TEMPLATE");
    }

    @Test
    @DisplayName("设备生效模版：无绑定时返回空态标记")
    void effective_shouldReportNone() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        when(deviceService.getDeviceOrThrow(DEVICE_ID)).thenReturn(device);
        when(alarmTemplateService.resolveEffectiveTemplate(device)).thenReturn(null);

        Result<Map<String, Object>> result = controller.effective(DEVICE_ID);

        assertThat(result.getData())
                .containsEntry("templateId", -1)
                .containsEntry("source", "NONE");
    }

    @Test
    @DisplayName("设备改绑端点透传空模板ID（跟随类型）")
    void bindDevice_shouldPassNullThrough() {
        Result<Void> result = controller.bindDevice(DEVICE_ID, null);

        assertThat(result.getCode()).isEqualTo(200);
    }
}
