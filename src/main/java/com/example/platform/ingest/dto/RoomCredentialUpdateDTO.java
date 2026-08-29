package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 机房凭证自动注册配置更新请求（合一模型下的凭证唯一可管理面）
 */
@Schema(description = "机房凭证自动注册配置更新请求")
@Data
public class RoomCredentialUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "首包自动注册开关：1=开启 0=关闭（可空不改）", example = "1")
    private Integer autoRegister;

    @Schema(description = "自动注册默认设备类型ID（指标推断失败时兜底；可空不改）", example = "4")
    private Integer defaultTypeId;
}
