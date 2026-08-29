package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 指标资源传输包（组织私有指标+模板的导出/导入载体，按编码关联、不携带主键ID）
 */
@Schema(description = "指标资源传输包（指标+模板，导出/导入用）")
@Data
public class PointResourceExportPackage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "包内指标列表")
    private List<PointItem> points;

    @Schema(description = "包内模板列表")
    private List<TemplateItem> templates;

    /**
     * 指标条目
     */
    @Schema(description = "指标条目")
    @Data
    public static class PointItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "指标编码", example = "temperature")
        private String pointCode;

        @Schema(description = "指标名称", example = "温度")
        private String pointName;

        @Schema(description = "单位", example = "℃")
        private String unit;

        @Schema(description = "数据类型", example = "NUMBER")
        private String dataType;

        @Schema(description = "描述")
        private String description;
    }

    /**
     * 模板条目
     */
    @Schema(description = "模板条目")
    @Data
    public static class TemplateItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "设备类型编码", example = "TEMP_HUMIDITY")
        private String typeCode;

        @Schema(description = "模板指标编码列表（含必选与排序）")
        private List<TemplatePointRef> items;
    }

    /**
     * 模板指标引用
     */
    @Schema(description = "模板指标引用")
    @Data
    public static class TemplatePointRef implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "指标编码", example = "temperature")
        private String pointCode;

        @Schema(description = "是否必选", example = "true")
        private Boolean required;

        @Schema(description = "排序", example = "1")
        private Integer sortOrder;
    }
}
