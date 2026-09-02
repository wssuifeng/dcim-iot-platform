package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义报文 dry-run 转译预览结果 VO（ADR-009）
 */
@Schema(description = "自定义报文转译预览结果")
@Data
public class PayloadDryRunVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "命中的模板（按匹配顺序第一个特征全中的模板；null=无模板命中）")
    private PayloadMappingDTO matchedMapping;

    @Schema(description = "转译出的设备编码（null=设备编码路径/固定值均未取到）")
    private String deviceCode;

    @Schema(description = "转译出的采样项（pointCode/value/sampleTime）")
    private List<IngestSampleItemDTO> samples = new ArrayList<>();

    @Schema(description = "转译说明（未命中模板/设备编码缺失/无有效字段等提示）")
    private List<String> notes = new ArrayList<>();
}
