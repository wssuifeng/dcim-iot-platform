package com.example.platform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备信息 Excel 导出行映射 DTO
 */
@Data
public class DeviceExportRow {

    @ExcelProperty("设备ID")
    private Integer id;

    @ExcelProperty("设备名称")
    private String deviceName;

    @ExcelProperty("设备编码")
    private String deviceCode;

    @ExcelProperty("类型名称")
    private String typeName;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("位置")
    private String location;

    @ExcelProperty("IP地址")
    private String ipAddress;

    @ExcelProperty("最后在线时间")
    private LocalDateTime lastOnlineTime;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;
}
