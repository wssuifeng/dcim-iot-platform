package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * MQTT 子凭证签发/重置响应 VO（S8-5：密码仅此一次返回，请妥善保存）
 */
@Schema(description = "MQTT 子凭证签发/重置响应（密码仅此一次返回）")
@Data
@EqualsAndHashCode(callSuper = true)
public class IngestMqttSubCredentialVO extends IngestCredentialVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "MQTT 连接用户名（开放命名空间 dcim-open/{mqttUsername}/）", example = "mqtt-8c31e0d2f4a6b9c0d2e3")
    private String mqttUsername;

    @Schema(description = "MQTT 连接密码（仅此一次返回，之后的查询永不返回）")
    private String mqttPassword;
}
