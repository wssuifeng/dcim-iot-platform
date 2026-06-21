package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.NotificationLog;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:通知发送日志数据访问接口，提供通知日志的CRUD操作
 * */
@Mapper
public interface NotificationLogMapper extends BaseMapper<NotificationLog> {
}
