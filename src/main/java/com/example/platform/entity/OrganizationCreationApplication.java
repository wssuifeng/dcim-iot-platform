package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization_creation_application")
public class OrganizationCreationApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantUserId;
    private String proposedOrgName;
    private String proposedOrgCode;
    private String description;
    private String status;
    private Long reviewerUserId;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private Long resultingOrganizationId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
