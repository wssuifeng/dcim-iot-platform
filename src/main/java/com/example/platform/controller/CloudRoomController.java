package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomCreatedVO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.CloudRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 云机房管理接口层（机房生命周期管理）
 *
 * <p>权限边界：
 * <ul>
 *   <li>写操作（创建/更新/停用）：仅 ADMIN（@PreAuthorize 兜底）</li>
 *   <li>读操作：任何已认证用户可调用</li>
 * </ul>
 * 机房删除须机房下无设备且无绑定网关凭证，由 Service 校验兜底。</p>
 */
@Tag(name = "云机房管理", description = "机房生命周期管理（ADMIN 写，已认证用户读）")
@RestController
@RequestMapping("/cloud-rooms")
@Validated
public class CloudRoomController {

    private final CloudRoomService cloudRoomService;
    private final com.example.platform.ingest.service.IngestCredentialService ingestCredentialService;
    private final CurrentUserContext currentUserContext;

    public CloudRoomController(CloudRoomService cloudRoomService,
                               com.example.platform.ingest.service.IngestCredentialService ingestCredentialService,
                               CurrentUserContext currentUserContext) {
        this.cloudRoomService = cloudRoomService;
        this.ingestCredentialService = ingestCredentialService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询机房", description = "支持按名称关键字与状态筛选，按创建时间倒序")
    @GetMapping("/page")
    public Result<Page<CloudRoom>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10，上限100") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "机房名称关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选：0-正常 1-停用（可选）") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.pageCloudRooms(
                user.userId(), current, size, keyword, status));
    }

    @Operation(summary = "查询机房详情", description = "机房不存在时返回 404")
    @GetMapping("/{id}")
    public Result<CloudRoom> getById(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.getCloudRoomOrThrow(id, user.userId()));
    }

    @Operation(summary = "创建机房",
            description = "ADMIN 专用；roomCode 全局唯一。同事务初始化该机房网关接入凭证，"
                    + "AccessKey/SecretKey 仅本次返回（合一模型：机房即接入主体）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_CLOUD_ROOM", detail = "机房编码:#createDTO.roomCode")
    public Result<CloudRoomCreatedVO> create(@Valid @RequestBody CloudRoomCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.createCloudRoom(createDTO, user.userId()));
    }

    @Operation(summary = "查询机房接入凭证", description = "凭证视图（AccessKey/状态/最近活跃/自动注册），SecretKey 永不返回")
    @GetMapping("/{id}/credential")
    public Result<com.example.platform.ingest.dto.IngestCredentialVO> getCredential(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        return Result.success(ingestCredentialService.getCredentialByRoomVO(id));
    }

    @Operation(summary = "更新机房凭证自动注册配置", description = "ADMIN 专用；自动注册开关与默认设备类型（兜底）")
    @PutMapping("/{id}/credential")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_ROOM_CREDENTIAL", detail = "机房ID:#id")
    public Result<com.example.platform.ingest.dto.IngestCredentialVO> updateCredential(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @RequestBody com.example.platform.ingest.dto.RoomCredentialUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.updateAutoRegister(
                id, updateDTO.getAutoRegister(), updateDTO.getDefaultTypeId(), user.userId()));
    }

    @Operation(summary = "重置机房接入密钥", description = "ADMIN 专用；旧 SecretKey 立即失效，新密钥仅本次返回")
    @PutMapping("/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "RESET_ROOM_SECRET", detail = "机房ID:#id")
    public Result<com.example.platform.ingest.dto.IngestCredentialCreatedVO> resetSecret(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.resetSecretByRoom(id, user.userId()));
    }

    @Operation(summary = "签发/重置 TB 兼容遥测 token",
            description = "ADMIN 专用（ADR-008）：/api/v1/{token}/telemetry 的凭证级 token，"
                    + "重置旧 token 立即失效；defaultDevice 为纯 TB 格式报文的默认设备编码（可空）")
    @PutMapping("/{id}/telemetry-token")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "RESET_ROOM_TELEMETRY_TOKEN", detail = "机房ID:#id")
    public Result<com.example.platform.ingest.dto.IngestCredentialVO> resetTelemetryToken(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String defaultDevice = body != null ? body.get("defaultDevice") : null;
        var credential = ingestCredentialService.issueTelemetryToken(id, defaultDevice, user.userId());
        com.example.platform.ingest.dto.IngestCredentialVO vo =
                com.example.platform.ingest.dto.IngestCredentialVO.fromEntity(credential);
        vo.setTelemetryToken(credential.getTelemetryToken());
        return Result.success(vo);
    }

    @Operation(summary = "签发/重置 MQTT 子凭证",
            description = "ADMIN 专用（S8-5）：MQTT 连接层用户名/密码（每机房一组，开放命名空间 "
                    + "dcim-open/{mqttUsername}/），重置旧密码立即失效；密码仅本次响应返回")
    @PutMapping("/{id}/mqtt-credential")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "RESET_ROOM_MQTT_CREDENTIAL", detail = "机房ID:#id")
    public Result<com.example.platform.ingest.dto.IngestMqttSubCredentialVO> resetMqttCredential(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        var credential = ingestCredentialService.issueMqttSubCredential(id, user.userId());
        com.example.platform.ingest.dto.IngestMqttSubCredentialVO vo =
                new com.example.platform.ingest.dto.IngestMqttSubCredentialVO();
        org.springframework.beans.BeanUtils.copyProperties(
                com.example.platform.ingest.dto.IngestCredentialVO.fromEntity(credential), vo);
        vo.setMqttUsername(credential.getMqttUsername());
        vo.setMqttPassword(credential.getMqttPassword());
        return Result.success(vo);
    }

    @Operation(summary = "更新机房信息", description = "ADMIN 专用；不允许修改 roomCode")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM", detail = "机房ID:#id")
    public Result<CloudRoom> update(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CloudRoomUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateCloudRoom(id, updateDTO, user.userId()));
    }

    @Operation(summary = "删除机房", description = "ADMIN 专用；机房下存在设备或网关凭证时拒绝删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_CLOUD_ROOM", detail = "机房ID:#id")
    public Result<Void> delete(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        cloudRoomService.deleteCloudRoom(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "启用/停用机房", description = "ADMIN 专用，软停用；status：0-正常 1-停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM_STATUS", detail = "机房ID:#id, 状态:#status")
    public Result<CloudRoom> updateStatus(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-停用", required = true) @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateStatus(id, status, user.userId()));
    }
}