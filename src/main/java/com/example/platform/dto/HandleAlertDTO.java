package com.example.platform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/*
 * 类功能:单条告警处理DTO，封装标记单条告警为已处理的请求参数
 * */
@Data
public class HandleAlertDTO {

    /** 处理人姓名 */
    @NotBlank(message = "处理人不能为空")
    private String handleUser;

    /** 告警ID */
    @NotNull(message = "告警ID不能为空")
    @Min(value = 1, message = "告警ID必须为正整数")
    private Integer alertId;
}
