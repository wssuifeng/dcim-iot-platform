package com.example.platform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录导出 Excel 行映射 DTO
 */
@Data
public class AlertExportRow {

    @ExcelProperty("告警ID")
    private Integer id;

    @ExcelProperty("设备ID")
    private Integer deviceId;

    @ExcelProperty("告警类型")
    private String alertType;

    @ExcelProperty("告警级别")
    private Integer alertLevel;

    @ExcelProperty("告警消息")
    private String alertMessage;

    @ExcelProperty("是否处理")
    private String handledText;

    @ExcelProperty("处理人")
    private String handleUser;

    @ExcelProperty("处理时间")
    private LocalDateTime handleTime;

    @ExcelProperty("产生时间")
    private LocalDateTime createTime;
}
