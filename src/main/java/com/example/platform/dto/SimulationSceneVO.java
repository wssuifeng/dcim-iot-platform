package com.example.platform.dto;

import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟场景 VO（含步骤列表）
 */
@Schema(description = "模拟场景响应")
@Data
public class SimulationSceneVO {

    @Schema(description = "场景ID")
    private Long id;

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "场景名称")
    private String sceneName;

    @Schema(description = "场景描述")
    private String description;

    @Schema(description = "所属机房ID")
    private Long cloudRoomId;

    @Schema(description = "目标设备类型编码")
    private String deviceTypeCode;

    @Schema(description = "状态：0-启用 1-停用")
    private Integer status;

    @Schema(description = "创建用户ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "包含步骤列表（仅详情接口返回）")
    private List<SimulationSceneStep> steps;

    public static SimulationSceneVO fromEntity(SimulationScene scene) {
        SimulationSceneVO vo = new SimulationSceneVO();
        vo.setId(scene.getId());
        vo.setSceneCode(scene.getSceneCode());
        vo.setSceneName(scene.getSceneName());
        vo.setDescription(scene.getDescription());
        vo.setCloudRoomId(scene.getCloudRoomId());
        vo.setDeviceTypeCode(scene.getDeviceTypeCode());
        vo.setStatus(scene.getStatus());
        vo.setCreatedBy(scene.getCreatedBy());
        vo.setCreateTime(scene.getCreateTime());
        vo.setUpdateTime(scene.getUpdateTime());
        return vo;
    }
}