package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.AlarmWebhookChannelSaveDTO;
import com.example.platform.dto.AlarmWebhookChannelVO;
import com.example.platform.service.AlarmWebhookChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警 webhook 渠道接口层
 *
 * <p>权限边界：全接口仅 ADMIN——渠道配置含 webhook URL 与渠道凭证（密钥/加签 secret），
 * 属于敏感管理面配置，不向 OPERATOR/VIEWER 开放。</p>
 */
@Tag(name = "告警webhook渠道", description = "IM webhook 通知渠道管理（ADMIN-only，含测试发送）")
@RestController
@RequestMapping("/webhook-channels")
@Validated
public class AlarmWebhookChannelController {

    private final AlarmWebhookChannelService webhookChannelService;

    public AlarmWebhookChannelController(AlarmWebhookChannelService webhookChannelService) {
        this.webhookChannelService = webhookChannelService;
    }

    @Operation(summary = "查询全部渠道配置")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<List<AlarmWebhookChannelVO>> listChannels() {
        return Result.success(webhookChannelService.listChannels());
    }

    @Operation(summary = "创建渠道配置")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_WEBHOOK_CHANNEL", detail = "#dto.name")
    @PostMapping
    public Result<AlarmWebhookChannelVO> createChannel(@Valid @RequestBody AlarmWebhookChannelSaveDTO dto) {
        return Result.success(webhookChannelService.createChannel(dto));
    }

    @Operation(summary = "更新渠道配置")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_WEBHOOK_CHANNEL", detail = "#id")
    @PutMapping("/{id}")
    public Result<AlarmWebhookChannelVO> updateChannel(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AlarmWebhookChannelSaveDTO dto) {
        return Result.success(webhookChannelService.updateChannel(id, dto));
    }

    @Operation(summary = "删除渠道配置")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_WEBHOOK_CHANNEL", detail = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> deleteChannel(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id) {
        webhookChannelService.deleteChannel(id);
        return Result.success(null);
    }

    @Operation(summary = "企微智能机器人已捕获会话",
            description = "群聊 chatid 只能从回调获取：在群里 @机器人发一句话后，会话即出现在此列表（S8-2 长连接模式）")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/wecom-aibot/chats")
    public Result<java.util.List<com.example.platform.ingest.wecom.WecomAibotChatRegistry.ChatEntry>> listAibotChats(
            @Parameter(description = "机器人 BotID", required = true) @RequestParam String botId) {
        return Result.success(webhookChannelService.listAibotChats(botId));
    }

    @Operation(summary = "测试发送",
            description = "发送标注[测试]的样例报文验证连通性；不产生告警事件、不写通知日志")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/test-send")
    public Result<String> testSend(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id) {
        return Result.success(webhookChannelService.testSend(id));
    }
}
