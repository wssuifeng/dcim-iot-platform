package com.example.platform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据导出 Excel 行映射 DTO
 */
@Data
public class DeviceDataExportRow {

    @ExcelProperty("设备ID")
    private Integer deviceId;

    @ExcelProperty("指标")
    private String metric;

    @ExcelProperty("数值")
    private BigDecimal dataValue;

    @ExcelProperty("采集时间")
    private LocalDateTime collectTime;
}
