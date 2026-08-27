package com.example.platform.ingest.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
import java.time.LocalDateTime;

/**
 * 网关接入凭证实体类，映射 ingest_credential 表，机器身份（通信主体）
 */
@Schema(description = "网关接入凭证")
@Data
@TableName("ingest_credential")
public class IngestCredential implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "接入标识（明文传输，全局唯一）", example = "gw-3f9a2c7d1e5b8a04", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "接入标识不能为空")
    @Size(max = 64, message = "接入标识最长64个字符")
    @TableField("access_key")
    private String accessKey;

    @Schema(description = "签名密钥（仅创建/重置时向管理员展示一次，查询接口永不返回）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "签名密钥不能为空")
    @Size(max = 100, message = "签名密钥最长100个字符")
    private String secret;

    @Schema(description = "网关显示名", example = "机房A-动环主机01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "网关名称不能为空")
    @Size(max = 100, message = "网关名称最长100个字符")
    @TableField("gateway_name")
    private String gatewayName;

    @Schema(description = "绑定机房ID（该凭证只能上报此机房设备）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "仿真网关标记：0-真实网关，1=机房仿真服务专用凭证", example = "0")
    @TableField("simulated")
    private Integer simulated;

    @Schema(description = "首包自动注册：1=收到未知 deviceCode 自动建档", example = "0")
    @TableField("auto_register")
    private Integer autoRegister;

    @Schema(description = "自动注册设备默认类型ID（auto_register=1 时必填）", example = "4")
    @TableField("default_type_id")
    private Integer defaultTypeId;

    @Schema(description = "状态：0-启用，1-停用", example = "0")
    private Integer status;

    @Schema(description = "最近心跳/上报时间（离线观测依据）", example = "2026-08-27T10:00:00")
    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    @Schema(description = "凭证过期时间（可空=永久）", example = "2027-01-01T00:00:00")
    @TableField("expire_at")
    private LocalDateTime expireAt;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
