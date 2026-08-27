package com.example.platform.dto;

import com.example.platform.entity.CloudRoom;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 机房创建结果 VO（含一次性展示的配套网关凭证——机房与凭证合一模型）
 */
@Schema(description = "机房创建结果（含一次性 AccessKey/SecretKey）")
@Data
public class CloudRoomCreatedVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机房ID", example = "1")
    private Long id;

    @Schema(description = "机房名称", example = "南昌红谷滩-铭万机房")
    private String roomName;

    @Schema(description = "机房编码", example = "B01")
    private String roomCode;

    @Schema(description = "位置", example = "南昌红谷滩")
    private String location;

    @Schema(description = "接入标识（网关侧配置项之一）", example = "gw-3f9a2c7d1e5b8a04")
    private String accessKey;

    @Schema(description = "签名密钥（仅此一次返回，网关侧妥善保存）", example = "sk-8c31e0d2f4a6b9...")
    private String secret;

    /**
     * @param room 机房实体
     * @param credential 配套凭证（含一次性 secret）
     */
    public static CloudRoomCreatedVO of(CloudRoom room,
                                        com.example.platform.ingest.dto.IngestCredentialCreatedVO credential) {
        CloudRoomCreatedVO vo = new CloudRoomCreatedVO();
        vo.setId(room.getId());
        vo.setRoomName(room.getRoomName());
        vo.setRoomCode(room.getRoomCode());
        vo.setLocation(room.getLocation());
        vo.setAccessKey(credential.getAccessKey());
        vo.setSecret(credential.getSecret());
        return vo;
    }
}
