package com.example.platform.dto;

import com.example.platform.entity.DeviceData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 设备数据批量上报DTO，封装一次批量上报的多条设备采集数据
 */
@Schema(description = "设备数据批量上报请求")
@Data
public class DeviceDataBatchDTO {

    @Schema(description = "设备数据列表（一次批量最多1000条）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "数据列表不能为空")
    @Valid
    private List<DeviceData> dataList;
}
