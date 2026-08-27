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

    @Schema(description = "网关显示名", example = "机房A-动环主机01")
    private String gatewayName;

    @Schema(description = "绑定机房ID", example = "1")
    private Long cloudRoomId;

    @Schema(description = "仿真网关标记：0-真实网关，1=仿真服务专用", example = "0")
    private Integer simulated;

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
        vo.setGatewayName(entity.getGatewayName());
        vo.setCloudRoomId(entity.getCloudRoomId());
        vo.setSimulated(entity.getSimulated());
        vo.setStatus(entity.getStatus());
        vo.setLastSeenAt(entity.getLastSeenAt());
        vo.setExpireAt(entity.getExpireAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
