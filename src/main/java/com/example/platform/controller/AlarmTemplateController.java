package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.AlarmTemplateItemCreateDTO;
import com.example.platform.dto.AlarmTemplateItemUpdateDTO;
import com.example.platform.dto.AlarmTemplateItemVO;
import com.example.platform.dto.AlarmTemplateSaveDTO;
import com.example.platform.dto.AlarmTemplateVO;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.Device;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 告警模版接口层（模版与规则行 CRUD、复制、设备/类型绑定管理）
 *
 * <p>权限边界：模版增删与绑定管理仅 ADMIN；规则行写入 ADMIN/OPERATOR；
 * 查询任何已认证用户（路由级 BUSINESS_READ_ROLES）。</p>
 */
@Tag(name = "告警模版", description = "可命名的告警规则融合模版：模版 CRUD、规则行管理、设备/类型绑定")
@RestController
@RequestMapping("/alarm-templates")
@Validated
public class AlarmTemplateController {

    private final AlarmTemplateService alarmTemplateService;
    private final DeviceService deviceService;

    public AlarmTemplateController(AlarmTemplateService alarmTemplateService,
                                   DeviceService deviceService) {
        this.alarmTemplateService = alarmTemplateService;
        this.deviceService = deviceService;
    }

    @Operation(summary = "分页查询告警模版", description = "支持名称/编码模糊与状态筛选，含引用计数（规则行数/绑定类型数/改绑设备数）")
    @GetMapping("/page")
    public Result<Page<AlarmTemplateVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "名称或编码关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0启用 1停用") @RequestParam(required = false) Integer status) {
        return Result.success(alarmTemplateService.pageTemplates(current, size, keyword, status));
    }

    @Operation(summary = "查询全部启用模版", description = "绑定下拉数据源（类型基础模版/设备改绑选择器）")
    @GetMapping
    public Result<List<AlarmTemplateVO>> listEnabled() {
        return Result.success(alarmTemplateService.pageTemplates(1, 200, null, 0).getRecords());
    }

    @Operation(summary = "查询模版详情", description = "含引用计数")
    @GetMapping("/{id}")
    public Result<AlarmTemplateVO> get(
            @Parameter(description = "模版ID", required = true) @PathVariable Long id) {
        return Result.success(alarmTemplateService.getTemplate(id));
    }

    @Operation(summary = "查询模版规则行", description = "按指标分组级别排序，含指标名称/编码/单位回填")
    @GetMapping("/{id}/items")
    public Result<List<AlarmTemplateItemVO>> items(
            @Parameter(description = "模版ID", required = true) @PathVariable Long id) {
        return Result.success(alarmTemplateService.listItems(id));
    }

    @Operation(summary = "创建告警模版", description = "模版编码全局唯一")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "CREATE_ALARM_TEMPLATE", detail = "模版编码:#dto.templateCode, 名称:#dto.templateName")
    public Result<AlarmTemplateVO> create(@Valid @RequestBody AlarmTemplateSaveDTO dto) {
        return Result.success(alarmTemplateService.createTemplate(dto));
    }

    @Operation(summary = "更新告警模版", description = "编码唯一校验排除自身")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "UPDATE_ALARM_TEMPLATE", detail = "模版ID:#id")
    public Result<AlarmTemplateVO> update(
            @Parameter(description = "模版ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AlarmTemplateSaveDTO dto) {
        return Result.success(alarmTemplateService.updateTemplate(id, dto));
    }

    @Operation(summary = "删除告警模版", description = "被设备类型或设备绑定时拒绝删除（4047，提示影响面）；仅 ADMIN")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_ALARM_TEMPLATE", detail = "模版ID:#id")
    public Result<Void> delete(
            @Parameter(description = "模版ID", required = true) @PathVariable Long id) {
        alarmTemplateService.deleteTemplate(id);
        return Result.success();
    }

    @Operation(summary = "复制告警模版", description = "自定义模版的常规起点：复制基础模版后修改；规则行全量拷贝")
    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "DUPLICATE_ALARM_TEMPLATE", detail = "源模版ID:#id")
    public Result<AlarmTemplateVO> duplicate(
            @Parameter(description = "源模版ID", required = true) @PathVariable Long id,
            @Parameter(description = "新模版编码", required = true) @RequestParam String newCode,
            @Parameter(description = "新模版名称") @RequestParam(required = false) String newName) {
        return Result.success(alarmTemplateService.duplicateTemplate(id, newCode, newName));
    }

    @Operation(summary = "新增模版规则行", description = "形状校验复用 METRIC/OFFLINE/COMM 契约；同模版同指标同比较符唯一")
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "CREATE_ALARM_TEMPLATE_ITEM", detail = "模版ID:#id, 规则名:#dto.ruleName")
    public Result<AlarmTemplateItemVO> createItem(
            @Parameter(description = "模版ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AlarmTemplateItemCreateDTO dto) {
        return Result.success(alarmTemplateService.createItem(id, dto));
    }

    @Operation(summary = "更新模版规则行", description = "指标与规则类型不可变更")
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "UPDATE_ALARM_TEMPLATE_ITEM", detail = "规则行ID:#itemId")
    public Result<AlarmTemplateItemVO> updateItem(
            @Parameter(description = "规则行ID", required = true) @PathVariable Long itemId,
            @Valid @RequestBody AlarmTemplateItemUpdateDTO dto) {
        return Result.success(alarmTemplateService.updateItem(itemId, dto));
    }

    @Operation(summary = "删除模版规则行", description = "仅 ADMIN")
    @DeleteMapping("/items/{itemId}")
    @AuditLog(operation = "DELETE_ALARM_TEMPLATE_ITEM", detail = "规则行ID:#itemId")
    public Result<Void> deleteItem(
            @Parameter(description = "规则行ID", required = true) @PathVariable Long itemId) {
        alarmTemplateService.deleteItem(itemId);
        return Result.success();
    }

    @Operation(summary = "查询设备生效告警模版", description = "解析设备改绑模版→类型基础模版，返回模版ID/名称/来源层级，供设备页展示")
    @GetMapping("/devices/{deviceId}/effective")
    public Result<Map<String, Object>> effective(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        Device device = deviceService.getDeviceOrThrow(deviceId);
        AlarmTemplate template = alarmTemplateService.resolveEffectiveTemplate(device);
        if (template == null) {
            return Result.success(Map.of("templateId", -1, "templateName", "无生效模版",
                    "source", "NONE"));
        }
        String source = device.getAlarmTemplateId() != null
                && device.getAlarmTemplateId().equals(template.getId()) ? "DEVICE_OVERRIDE" : "TEMPLATE";
        return Result.success(Map.of(
                "templateId", template.getId(),
                "templateName", template.getTemplateName(),
                "source", source));
    }

    @Operation(summary = "设备改绑告警模版", description = "templateId 传空=跟随类型基础模版；停用模版拒绝绑定")
    @PutMapping("/devices/{deviceId}/binding")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "BIND_DEVICE_ALARM_TEMPLATE", detail = "设备ID:#deviceId")
    public Result<Void> bindDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "模版ID（空=跟随类型）") @RequestParam(required = false) Long templateId) {
        alarmTemplateService.updateDeviceBinding(deviceId, templateId);
        return Result.success();
    }

    @Operation(summary = "类型绑定基础告警模版", description = "templateId 传空=解绑；仅 ADMIN；停用模版拒绝绑定")
    @PutMapping("/types/{typeId}/binding")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "BIND_TYPE_ALARM_TEMPLATE", detail = "类型ID:#typeId")
    public Result<Void> bindType(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Parameter(description = "模版ID（空=解绑）") @RequestParam(required = false) Long templateId) {
        alarmTemplateService.updateTypeBinding(typeId, templateId);
        return Result.success();
    }
}
