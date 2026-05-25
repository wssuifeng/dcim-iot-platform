package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

/*
 * 类功能:安全审计日志数据访问接口，提供审计日志的写入和过期清理方法
 * */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    /*
     * 方法功能:删除指定时间之前的所有审计日志，用于数据保留策略的定时清理
     * 参数:beforeTime-保留截止时间（该时间之前的日志将被删除）
     * 返回数据:int-删除的记录条数
     * */
    @Delete("DELETE FROM sys_audit_log WHERE create_time < #{beforeTime}")
    int deleteByCreateTimeBefore(@Param("beforeTime") LocalDateTime beforeTime);
}