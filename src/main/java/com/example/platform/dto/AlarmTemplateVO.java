package com.example.platform.dto;

import com.example.platform.entity.AlarmTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警模版响应 VO（含引用计数与指标展示回填）
 */
@Schema(description = "告警模版响应")
@Data
public class AlarmTemplateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模版ID")
    private Long id;

    @Schema(description = "模版编码", example = "TEMP_HUM_STD")
    private String templateCode;

    @Schema(description = "模版显示名", example = "温湿度标准监控")
    private String templateName;

    @Schema(description = "用途说明")
    private String description;

    @Schema(description = "状态：0-启用 1-停用", example = "0")
    private Integer status;

    @Schema(description = "规则行数量（全部）")
    private Long itemCount;

    @Schema(description = "绑定该模版的设备类型数")
    private Long typeRefCount;

    @Schema(description = "改绑该模版的设备数")
    private Long deviceRefCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public static AlarmTemplateVO fromEntity(AlarmTemplate template) {
        AlarmTemplateVO vo = new AlarmTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setDescription(template.getDescription());
        vo.setStatus(template.getStatus());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
