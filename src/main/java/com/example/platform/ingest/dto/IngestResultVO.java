package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 网关上报处理结果 VO（逐条独立事务，部分成功合法；重复计入 duplicated 不报错）
 */
@Schema(description = "网关上报处理结果（部分成功合法，QoS1 重发计入 duplicated）")
@Data
public class IngestResultVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "成功写入条数", example = "198")
    private int accepted;

    @Schema(description = "幂等去重跳过条数（deviceCode+pointCode+seq 重复）", example = "2")
    private int duplicated;

    @Schema(description = "失败条数", example = "0")
    private int failed;

    @Schema(description = "失败明细（按请求下标）")
    private List<FailureItem> failures;

    /**
     * 单条失败明细
     */
    @Schema(description = "上报单条失败明细")
    @Data
    public static class FailureItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "请求列表中的下标（从0开始）", example = "3")
        private int index;

        @Schema(description = "错误码", example = "4023")
        private int errorCode;

        @Schema(description = "错误信息", example = "设备不存在: UNKNOWN-DEV")
        private String message;
    }
}
