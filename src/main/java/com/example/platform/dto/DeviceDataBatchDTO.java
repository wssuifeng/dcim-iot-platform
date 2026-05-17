package com.example.platform.dto;

import com.example.platform.entity.DeviceData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DeviceDataBatchDTO {
    @Valid
    @NotNull(message = "数据列表不能为空")
    @Size(min = 1, max = 1000, message = "单次上报数量限制1-1000条")
    private List<DeviceData> dataList;
}