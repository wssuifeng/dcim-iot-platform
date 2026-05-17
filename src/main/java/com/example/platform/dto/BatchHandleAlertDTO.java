package com.example.platform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchHandleAlertDTO {
    @NotEmpty(message = "告警ID列表不能为空")
    @Size(max = 100, message = "单次最多处理100条告警")
    private List<@Min(1) Integer> ids;

    @NotBlank(message = "处理人不能为空")
    @Size(max = 50, message = "处理人姓名不能超过50字符")
    private String handleUser;
}