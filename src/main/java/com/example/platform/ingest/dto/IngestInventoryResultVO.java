package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 网关设备清单处理结果 VO
 */
@Schema(description = "网关设备清单处理结果")
@Data
public class IngestInventoryResultVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "本次新建设备数", example = "3")
    private int registered;

    @Schema(description = "平台已存在（跳过）设备数", example = "1")
    private int exists;

    @Schema(description = "处理失败设备数", example = "0")
    private int failed;

    @Schema(description = "清单已收到但未导入（auto_register 关闭时全部为 unknown）", example = "0")
    private int unknown;

    @Schema(description = "逐设备结果")
    private List<ItemResult> results;

    /**
     * 单设备结果
     */
    @Schema(description = "清单单设备处理结果")
    @Data
    public static class ItemResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "设备编码", example = "TH-ROOM1-B")
        private String deviceCode;

        @Schema(description = "结果：registered-新建 / exists-已存在 / failed-失败 / unknown-未导入", example = "registered")
        private String result;

        @Schema(description = "说明（失败原因或绑定数）", example = "已建绑定2个")
        private String message;
    }
}
