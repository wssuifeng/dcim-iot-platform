package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警 webhook 渠道保存请求 DTO（创建/更新共用）
 */
@Schema(description = "告警 webhook 渠道保存请求")
@Data
public class AlarmWebhookChannelSaveDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "渠道显示名", example = "运维值班群", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 100, message = "渠道名称最长100个字符")
    private String name;

    @Schema(description = "渠道类型", example = "WECOM",
            allowableValues = {"WECOM", "WECOM_AIBOT", "DINGTALK", "FEISHU", "NTFY", "TELEGRAM", "GENERIC"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "渠道类型不能为空")
    @Pattern(regexp = "^(WECOM|WECOM_AIBOT|DINGTALK|FEISHU|NTFY|TELEGRAM|GENERIC)$", message = "渠道类型不合法")
    private String channelType;

    @Schema(description = "webhook 地址（各平台机器人创建后获取）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "webhook 地址不能为空")
    @Size(max = 500, message = "webhook 地址最长500个字符")
    private String webhookUrl;

    @Schema(description = "渠道凭证 JSON（钉钉/飞书 secret、Telegram chat_id、ntfy token、通用 authHeader）",
            example = "{\"secret\":\"SECxxx\"}")
    @Size(max = 2000, message = "凭证 JSON 最长2000个字符")
    private String credentialJson;

    @Schema(description = "推送条件：alarm_level ≤ max_alarm_level 即推送（1=最紧急）", example = "3")
    @Min(value = 1, message = "最高推送级别为1")
    @Max(value = 5, message = "最低推送级别为5")
    private Integer maxAlarmLevel;

    @Schema(description = "是否启用，默认false", example = "true")
    private Boolean enabled = Boolean.FALSE;
}
