package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization_join_application")
public class OrganizationJoinApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantUserId;
    private Long organizationId;
    private String requestNote;
    private String status;
    private String assignedMembershipRole;
    private Long reviewerUserId;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
