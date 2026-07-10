package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 告警处理动作请求 DTO
 *
 * <p>note 为可选的处理说明，写入 AlarmHandlingRecord.handling_note。</p>
 */
@Schema(description = "告警处理动作请求")
@Data
public class AlarmHandlingActionDTO {

    @Schema(description = "处理说明（可选，写入处理记录）")
    @Size(max = 500, message = "处理说明最长500个字符")
    private String note;
}