package com.example.platform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标采样导出行 DTO（EasyExcel 映射，承接旧 device-data 导出能力）
 */
@Schema(description = "指标采样导出行")
@Data
public class MetricSampleExportRow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("采样ID")
    @Schema(description = "采样ID")
    private Long id;

    @ExcelProperty("设备ID")
    @Schema(description = "设备ID")
    private Integer deviceId;

    @ExcelProperty("指标编码")
    @Schema(description = "指标编码")
    private String pointCode;

    @ExcelProperty("指标名称")
    @Schema(description = "指标名称")
    private String pointName;

    @ExcelProperty("采样值")
    @Schema(description = "采样值")
    private BigDecimal sampleValue;

    @ExcelProperty("采样时间")
    @Schema(description = "采样时间（设备侧）")
    private LocalDateTime sampleTime;

    @ExcelProperty("接收时间")
    @Schema(description = "平台接收时间")
    private LocalDateTime receiveTime;
}
