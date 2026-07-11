package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.PlatformRegistrationSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformRegistrationSettingsMapper extends BaseMapper<PlatformRegistrationSettings> {

    @Select("SELECT * FROM platform_registration_settings WHERE id = 1 FOR UPDATE")
    PlatformRegistrationSettings selectSingletonForUpdate();
}
