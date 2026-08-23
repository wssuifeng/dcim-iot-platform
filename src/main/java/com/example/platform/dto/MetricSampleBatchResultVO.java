package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量采样写入结果 VO（逐条独立处理，部分成功合法）
 */
@Schema(description = "批量采样写入结果（逐条独立，部分成功合法）")
@Data
public class MetricSampleBatchResultVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "成功写入条数", example = "99")
    private int successCount;

    @Schema(description = "失败明细（按请求下标）")
    private List<FailureItem> failures;

    /**
     * 单条失败明细
     */
    @Schema(description = "批量写入单条失败明细")
    @Data
    public static class FailureItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "请求列表中的下标（从0开始）", example = "3")
        private int index;

        @Schema(description = "错误码", example = "4033")
        private int errorCode;

        @Schema(description = "错误信息", example = "设备点位未绑定或已停用")
        private String message;
    }
}
