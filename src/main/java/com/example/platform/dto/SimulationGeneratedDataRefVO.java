package com.example.platform.dto;

import com.example.platform.entity.SimulationGeneratedDataRef;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟生成数据引用 VO（含点位编码展示填充）
 */
@Schema(description = "模拟生成数据引用")
@Data
public class SimulationGeneratedDataRefVO {

    @Schema(description = "引用ID")
    private Long id;

    @Schema(description = "关联运行ID")
    private Long runId;

    @Schema(description = "关联步骤ID")
    private Long stepId;

    @Schema(description = "目标设备ID")
    private Integer deviceId;

    @Schema(description = "目标点位ID")
    private Long pointId;

    @Schema(description = "点位编码（展示用）")
    private String pointCode;

    @Schema(description = "点位名称（展示用）")
    private String pointName;

    @Schema(description = "单位（展示用）")
    private String unit;

    @Schema(description = "写入 metric_sample 主键（可追溯依据）")
    private Long metricSampleId;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "注入的采样值")
    private BigDecimal sampleValue;

    @Schema(description = "生成时间")
    private LocalDateTime generatedAt;

    public static SimulationGeneratedDataRefVO fromEntity(SimulationGeneratedDataRef ref) {
        SimulationGeneratedDataRefVO vo = new SimulationGeneratedDataRefVO();
        vo.setId(ref.getId());
        vo.setRunId(ref.getRunId());
        vo.setStepId(ref.getStepId());
        vo.setDeviceId(ref.getDeviceId());
        vo.setPointId(ref.getPointId());
        vo.setMetricSampleId(ref.getMetricSampleId());
        vo.setAction(ref.getAction());
        vo.setSampleValue(ref.getSampleValue());
        vo.setGeneratedAt(ref.getGeneratedAt());
        return vo;
    }
}