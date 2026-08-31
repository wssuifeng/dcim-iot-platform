package com.example.platform.ingest.dto;

import com.example.platform.ingest.entity.IngestCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关凭证视图 VO（secret 永不返回；仅创建/重置响应通过 CreatedVO 一次性展示）
 */
@Schema(description = "网关凭证视图")
@Data
public class IngestCredentialVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证ID", example = "1")
    private Long id;

    @Schema(description = "接入标识", example = "gw-3f9a2c7d1e5b8a04")
    private String accessKey;

    @Schema(description = "TB 兼容遥测 token（签发/重置响应一次性返回明文；其余查询返回掩码）")
    private String telemetryToken;

    @Schema(description = "TB 纯格式报文的默认设备编码")
    private String telemetryDefaultDevice;

    @Schema(description = "绑定机房ID", example = "1")
    private Long cloudRoomId;

    @Schema(description = "首包自动注册：1=收到未知 deviceCode 自动建档", example = "0")
    private Integer autoRegister;

    @Schema(description = "自动注册设备默认类型ID", example = "4")
    private Integer defaultTypeId;

    @Schema(description = "状态：0-启用，1-停用", example = "0")
    private Integer status;

    @Schema(description = "最近心跳/上报时间", example = "2026-08-27T10:00:00")
    private LocalDateTime lastSeenAt;

    @Schema(description = "凭证过期时间（可空=永久）", example = "2027-01-01T00:00:00")
    private LocalDateTime expireAt;

    @Schema(description = "创建时间", example = "2026-08-27T09:00:00")
    private LocalDateTime createTime;

    /**
     * @param entity 凭证实体
     */
    public static IngestCredentialVO fromEntity(IngestCredential entity) {
        IngestCredentialVO vo = new IngestCredentialVO();
        vo.setId(entity.getId());
        vo.setAccessKey(entity.getAccessKey());
        // token 掩码返回：明文仅在签发/重置路径由服务层覆写
        vo.setTelemetryToken(entity.getTelemetryToken() != null
                ? entity.getTelemetryToken().substring(0, Math.min(8, entity.getTelemetryToken().length())) + "…"
                : null);
        vo.setTelemetryDefaultDevice(entity.getTelemetryDefaultDevice());
        vo.setCloudRoomId(entity.getCloudRoomId());
        vo.setAutoRegister(entity.getAutoRegister());
        vo.setDefaultTypeId(entity.getDefaultTypeId());
        vo.setStatus(entity.getStatus());
        vo.setLastSeenAt(entity.getLastSeenAt());
        vo.setExpireAt(entity.getExpireAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
