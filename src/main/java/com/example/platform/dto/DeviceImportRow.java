package com.example.platform.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 设备批量导入 Excel 行映射 DTO
 */
@Data
public class DeviceImportRow {

    @ExcelProperty("设备名称")
    private String deviceName;

    @ExcelProperty("设备编码")
    private String deviceCode;

    @ExcelProperty("类型编码")
    private String typeCode;

    @ExcelProperty("组织编码")
    private String organizationCode;

    @ExcelProperty("机房编码")
    private String cloudRoomCode;

    @ExcelProperty("位置")
    private String location;

    @ExcelProperty("IP地址")
    private String ipAddress;
}
