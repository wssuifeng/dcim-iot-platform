package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.common.util.ExcelExportUtil;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DeviceExportRow;
import com.example.platform.dto.DeviceImportResult;
import com.example.platform.dto.DeviceImportRow;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.CloudRoom;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备管理接口层，提供设备的CRUD、状态更新、搜索分页等RESTful接口
 */
@Tag(name = "设备管理", description = "设备 CRUD、状态更新、搜索分页")
@RestController
@RequestMapping("/devices")
@Validated
public class DeviceController {
    private final DeviceService deviceService;
    private final DeviceTypeService deviceTypeService;
    private final CurrentUserContext currentUserContext;
    private final CloudRoomService cloudRoomService;

    public DeviceController(DeviceService deviceService,
                            DeviceTypeService deviceTypeService,
                            CurrentUserContext currentUserContext,
                            CloudRoomService cloudRoomService) {
        this.deviceService = deviceService;
        this.deviceTypeService = deviceTypeService;
        this.currentUserContext = currentUserContext;
        this.cloudRoomService = cloudRoomService;
    }

    @Operation(summary = "查询所有设备列表", description = "返回设备信息不分页；可选按所属机房过滤")
    @GetMapping
    public Result<List<Device>> list(
            @Parameter(description = "所属机房ID筛选（可选）") @RequestParam(required = false) Long cloudRoomId) {
        return Result.success(deviceService.listByConditionScoped(null, null, cloudRoomId,
                currentUser().userId()));
    }

    @Operation(summary = "设备编码可用性检查", description = "添加设备时实时防重：返回编码是否可用")
    @GetMapping("/code-available")
    public Result<Map<String, Object>> codeAvailable(
            @Parameter(description = "待检查设备编码", required = true) @RequestParam String code) {
        boolean available = deviceService.findByCode(code) == null;
        return Result.success(Map.of("available", available));
    }

    @Operation(summary = "根据ID查询设备详情", description = "设备不存在时抛出 DeviceNotFoundException，由全局异常处理器返回 404")
    @GetMapping("/{id}")
    public Result<Device> getById(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id) {
        return Result.success(deviceService.getScoped(id, currentUser().userId()));
    }

    @Operation(summary = "新增设备", description = "使用 CreateGroup 分组校验；Service 层内部校验设备编码唯一性与设备类型存在性")
    @PostMapping
    @AuditLog(operation = "CREATE_DEVICE", detail = "设备名称:#device.deviceName, 设备编码:#device.deviceCode")
    public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
        deviceService.createScoped(device, currentUser().userId());
        return Result.success();
    }

    @Operation(summary = "更新设备信息", description = "以路径ID为准，防御性校验请求体ID一致性；乐观锁冲突时提示数据可能已被他人修改")
    @PutMapping("/{id}")
    @AuditLog(operation = "UPDATE_DEVICE", detail = "设备ID:#id, 设备名称:#device.deviceName")
    public Result<Void> update(@Parameter(description = "设备ID", required = true) @PathVariable Integer id,
                               @Validated(UpdateGroup.class) @RequestBody Device device) {
        if (device.getId() != null && !id.equals(device.getId())) {
            return Result.error(ResultCode.INVALID_PARAMETER, "请求ID不一致，无法更新");
        }
        deviceService.updateScoped(id, device, currentUser().userId());
        return Result.success();
    }

    @Operation(summary = "查询在线设备列表", description = "返回当前 status=1（在线）的全部设备")
    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        return Result.success(deviceService.findOnlineDevicesScoped(currentUser().userId()));
    }

    @Operation(summary = "模糊搜索设备", description = "按设备名称模糊匹配；关键词为空时返回 400")
    @GetMapping("/search")
    public Result<List<Device>> search(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.error(ResultCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        return Result.success(deviceService.findByNameLikeScoped(name, currentUser().userId()));
    }

    @Operation(summary = "更新设备状态", description = "status 取值：0-离线，1-在线，2-故障；超出范围返回 400")
    @PutMapping("/{id}/status")
    @AuditLog(operation = "UPDATE_DEVICE_STATUS", detail = "设备ID:#id, 状态:#status")
    public Result<Void> updateStatus(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id,
            @Parameter(description = "状态值：0-离线，1-在线，2-故障", required = true) @RequestParam Integer status) {
        if (status < 0 || status > 2) {
            return Result.error(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-离线，1-在线，2-故障");
        }
        deviceService.updateStatusScoped(id, status, currentUser().userId());
        return Result.success();
    }

    @Operation(summary = "分页查询设备", description = "支持按状态、名称、所属机房筛选，结果按 updateTime 倒序")
    @GetMapping("/page")
    public Result<Page<Device>> listByPage(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10，上限100") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备状态筛选（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "设备名称模糊筛选（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "所属机房ID筛选（可选）") @RequestParam(required = false) Long cloudRoomId) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(deviceService.pageByConditionScoped(
                pageParams[0], pageParams[1], status, name, cloudRoomId, currentUser().userId()));
    }

    @Operation(summary = "删除设备", description = "设备不存在时抛出 DeviceNotFoundException")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_DEVICE", detail = "设备ID:#id")
    public Result<Void> delete(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id) {
        deviceService.deleteScoped(id, currentUser().userId());
        return Result.success();
    }

    @Operation(summary = "批量导入设备", description = "上传 Excel 文件批量导入设备，单次上限 500 条，支持 .xlsx 格式")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导入完成（可能包含部分失败行）",
                    content = @Content(schema = @Schema(implementation = DeviceImportResult.class))),
            @ApiResponse(responseCode = "400", description = "文件为空(4061)/格式错误(4062)/超过500条(4063)")
    })
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "IMPORT_DEVICE", detail = "批量导入设备")
    public Result<DeviceImportResult> importDevices(
            @Parameter(description = "Excel 文件", required = true) @RequestParam("file") MultipartFile file) {
        return Result.success(deviceService.importFromExcelScoped(file, currentUser().userId()));
    }

    @Operation(summary = "下载导入模板", description = "下载设备批量导入 Excel 模板")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 模板文件")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        ExcelExportUtil.setExcelResponse(response, "设备导入模板");
        EasyExcel.write(response.getOutputStream(), DeviceImportRow.class)
                .sheet("设备导入模板")
                .doWrite(List.of());
    }

    @Operation(summary = "导出设备列表", description = "按状态、名称、机房筛选导出 Excel，单次上限 10000 条")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流"),
            @ApiResponse(responseCode = "400", description = "导出超过10000条(4065)"),
            @ApiResponse(responseCode = "403", description = "VIEWER 角色无权导出")
    })
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @AuditLog(operation = "EXPORT_DEVICE", detail = "导出设备列表")
    public void exportDevices(
            @Parameter(description = "设备状态筛选（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "设备名称模糊筛选（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "所属机房ID筛选（可选）") @RequestParam(required = false) Long cloudRoomId,
            HttpServletResponse response) throws IOException {
        List<Device> deviceList = deviceService.listByConditionScoped(
                status, name, cloudRoomId, currentUser().userId());
        if (deviceList.size() > 10000) {
            throw new com.example.platform.common.exception.BusinessException(
                    ResultCode.EXPORT_SIZE_EXCEED, "导出数据量超过 10000 条限制");
        }
        Map<Integer, String> typeNameMap = deviceTypeService.list().stream()
                .collect(Collectors.toMap(DeviceType::getId, DeviceType::getTypeName));
        List<DeviceExportRow> rows = deviceList.stream().map(d -> {
            DeviceExportRow row = new DeviceExportRow();
            row.setId(d.getId());
            row.setDeviceName(d.getDeviceName());
            row.setDeviceCode(d.getDeviceCode());
            row.setTypeName(typeNameMap.getOrDefault(d.getTypeId(), ""));
            CloudRoom room = cloudRoomService.getById(d.getCloudRoomId());
            row.setCloudRoomCode(room != null ? room.getRoomCode() : "");
            row.setStatusText(statusText(d.getStatus()));
            row.setLocation(d.getLocation());
            row.setIpAddress(d.getIpAddress());
            row.setLastOnlineTime(d.getLastOnlineTime());
            row.setCreateTime(d.getCreateTime());
            return row;
        }).collect(Collectors.toList());
        ExcelExportUtil.setExcelResponse(response, ExcelExportUtil.buildFileName("设备列表"));
        EasyExcel.write(response.getOutputStream(), DeviceExportRow.class)
                .sheet("设备列表")
                .doWrite(rows);
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "离线";
            case 1 -> "在线";
            case 2 -> "故障";
            default -> "未知";
        };
    }

    private CurrentUser currentUser() {
        return currentUserContext.requireCurrentUser();
    }
}
