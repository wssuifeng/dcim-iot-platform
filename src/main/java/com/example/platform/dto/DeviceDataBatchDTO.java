package com.example.platform.dto;

import com.example.platform.entity.DeviceData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/*
 * 类功能:设备数据批量上报DTO，封装一次批量上报的多条设备采集数据
 * */
@Data
public class DeviceDataBatchDTO {

    /** 设备数据列表 */
    @NotEmpty(message = "数据列表不能为空")
    @Valid
    private List<DeviceData> dataList;
}
