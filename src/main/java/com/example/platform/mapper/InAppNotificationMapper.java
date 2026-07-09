package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.InAppNotification;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:站内通知（已读状态）数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface InAppNotificationMapper extends BaseMapper<InAppNotification> {
}
