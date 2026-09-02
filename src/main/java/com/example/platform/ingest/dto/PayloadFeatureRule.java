package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 报文特征匹配规则 DTO（ADR-009）：模板路由依据，全部规则命中才使用该模板
 */
@Schema(description = "报文特征匹配规则")
@Data
public class PayloadFeatureRule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报文 JSON 内部路径（点路径+数组下标）", example = "model")
    @NotBlank(message = "特征路径不能为空")
    private String path;

    @Schema(description = "匹配操作：eq-等值 / contains-包含 / exists-存在且非空", example = "eq")
    @Pattern(regexp = "eq|contains|exists", message = "匹配操作仅支持 eq/contains/exists")
    private String op;

    @Schema(description = "比对值（exists 时忽略）", example = "TH-100")
    private String value;
}
