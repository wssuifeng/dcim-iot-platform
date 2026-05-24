package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/*
 * 类功能:批量处理告警DTO，封装批量标记告警为已处理的请求参数
 * */
@Data
public class BatchHandleAlertDTO {

    /** 待处理的告警ID列表 */
    @NotEmpty(message = "告警ID列表不能为空")
    @Size(max = 50, message = "单次最多处理50条告警")
    private List<Integer> ids;

    /** 处理人姓名 */
    @NotBlank(message = "处理人不能为空")
    @Size(max = 50, message = "处理人姓名不能超过50字符")
    private String handleUser;
}
