package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_registration_settings")
public class PlatformRegistrationSettings {
    @TableId
    private Integer id;
    private Boolean accountRegistrationEnabled;
    private String organizationRegistrationMode;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
