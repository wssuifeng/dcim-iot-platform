package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警 webhook 通知渠道实体类，映射 alarm_webhook_channel 表
 * 站内/邮件之外并行挂载的 IM webhook 渠道配置；URL 与凭证全部数据驱动不写死
 */
@Schema(description = "告警 webhook 通知渠道")
@Data
@TableName("alarm_webhook_channel")
public class AlarmWebhookChannel implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TYPE_WECOM = "WECOM";
    public static final String TYPE_WECOM_AIBOT = "WECOM_AIBOT";
    public static final String TYPE_DINGTALK = "DINGTALK";
    public static final String TYPE_FEISHU = "FEISHU";
    public static final String TYPE_NTFY = "NTFY";
    public static final String TYPE_TELEGRAM = "TELEGRAM";
    public static final String TYPE_GENERIC = "GENERIC";

    @Schema(description = "渠道ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "渠道显示名", example = "运维值班群", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 100, message = "渠道名称最长100个字符")
    @TableField("name")
    private String name;

    @Schema(description = "渠道类型", example = "WECOM",
            allowableValues = {"WECOM", "WECOM_AIBOT", "DINGTALK", "FEISHU", "NTFY", "TELEGRAM", "GENERIC"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道类型不能为空")
    @TableField("channel_type")
    private String channelType;

    @Schema(description = "webhook 地址（各平台机器人创建后获取）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "webhook 地址不能为空")
    @Size(max = 500, message = "webhook 地址最长500个字符")
    @TableField("webhook_url")
    private String webhookUrl;

    @Schema(description = "渠道凭证 JSON（钉钉/飞书加签 secret、Telegram chat_id、ntfy token、通用 authHeader 等）")
    @Size(max = 2000, message = "凭证 JSON 最长2000个字符")
    @TableField("credential_json")
    private String credentialJson;

    @Schema(description = "推送条件：alarm_level ≤ max_alarm_level 即推送（1=最紧急）", example = "3")
    @TableField("max_alarm_level")
    private Integer maxAlarmLevel;

    @Schema(description = "是否启用：0-停用 1-启用", example = "1")
    @TableField("enabled")
    private Integer enabled;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private java.time.LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private java.time.LocalDateTime updateTime;
}
