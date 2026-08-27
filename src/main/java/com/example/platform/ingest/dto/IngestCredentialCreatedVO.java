package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 网关凭证创建/重置响应 VO（secret 仅此一次返回，请网关侧妥善保存）
 */
@Schema(description = "网关凭证创建/重置响应（secret 仅此一次返回）")
@Data
@EqualsAndHashCode(callSuper = true)
public class IngestCredentialCreatedVO extends IngestCredentialVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "签名密钥（仅此一次返回，之后的查询永不返回）", example = "sk-8c31e0d2f4a6b9...")
    private String secret;
}
