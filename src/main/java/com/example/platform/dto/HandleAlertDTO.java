package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HandleAlertDTO {
    @NotBlank(message = "处理人不能为空")
    @Size(max = 50, message = "处理人姓名不能超过50字符")
    private String handleUser;
}