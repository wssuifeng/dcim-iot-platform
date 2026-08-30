package com.example.platform.dto;

import com.example.platform.entity.AlarmWebhookChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警 webhook 渠道响应 VO
 */
@Schema(description = "告警 webhook 渠道响应")
@Data
public class AlarmWebhookChannelVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "渠道ID")
    private Long id;

    @Schema(description = "渠道显示名")
    private String name;

    @Schema(description = "渠道类型")
    private String channelType;

    @Schema(description = "webhook 地址")
    private String webhookUrl;

    @Schema(description = "渠道凭证 JSON（ADMIN-only 接口；明文返回供编辑，加密存储为长期备忘）")
    private String credentialJson;

    @Schema(description = "推送条件：alarm_level ≤ max_alarm_level 即推送")
    private Integer maxAlarmLevel;

    @Schema(description = "是否启用：0停用 1启用")
    private Integer enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public static AlarmWebhookChannelVO fromEntity(AlarmWebhookChannel channel) {
        AlarmWebhookChannelVO vo = new AlarmWebhookChannelVO();
        vo.setId(channel.getId());
        vo.setName(channel.getName());
        vo.setChannelType(channel.getChannelType());
        vo.setWebhookUrl(channel.getWebhookUrl());
        vo.setCredentialJson(channel.getCredentialJson());
        vo.setMaxAlarmLevel(channel.getMaxAlarmLevel());
        vo.setEnabled(channel.getEnabled());
        vo.setCreateTime(channel.getCreateTime());
        vo.setUpdateTime(channel.getUpdateTime());
        return vo;
    }
}
