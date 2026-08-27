package com.example.platform.dto;

import com.example.platform.entity.SimulationRunRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟运行记录 VO
 */
@Schema(description = "模拟运行响应")
@Data
public class SimulationRunVO {

    @Schema(description = "运行ID")
    private Long id;

    @Schema(description = "关联场景ID")
    private Long sceneId;

    @Schema(description = "运行时场景快照（JSON）")
    private String sceneSnapshot;

    @Schema(description = "触发用户ID")
    private Long triggerUserId;

    @Schema(description = "所属机房ID")
    private Long cloudRoomId;

    @Schema(description = "状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED")
    private String status;

    @Schema(description = "步骤总数")
    private Integer totalSteps;

    @Schema(description = "成功步骤数")
    private Integer successSteps;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "开始执行时间")
    private LocalDateTime startTime;

    @Schema(description = "结束执行时间")
    private LocalDateTime endTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static SimulationRunVO fromEntity(SimulationRunRecord record) {
        SimulationRunVO vo = new SimulationRunVO();
        vo.setId(record.getId());
        vo.setSceneId(record.getSceneId());
        vo.setSceneSnapshot(record.getSceneSnapshot());
        vo.setTriggerUserId(record.getTriggerUserId());
        vo.setCloudRoomId(record.getCloudRoomId());
        vo.setStatus(record.getStatus());
        vo.setTotalSteps(record.getTotalSteps());
        vo.setSuccessSteps(record.getSuccessSteps());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}