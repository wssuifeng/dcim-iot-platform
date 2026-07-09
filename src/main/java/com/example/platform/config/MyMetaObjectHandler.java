package com.example.platform.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/*
 * 类功能:MyBatis-Plus自动填充处理器，在插入和更新时自动填充时间字段
 * */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /*
     * 方法功能:插入时自动填充collectTime、createTime和updateTime
     * 参数:metaObject-MyBatis-Plus元对象
     * 返回数据:无
     * */
    @Override
    public void insertFill(MetaObject metaObject) {
        // strictInsertFill仅在字段为null时填充，尊重手动传入值
        this.strictInsertFill(metaObject, "collectTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        // triggeredTime为AlarmEvent（alarm_event表）的插入自动填充字段，语义等同于createTime
        this.strictInsertFill(metaObject, "triggeredTime", LocalDateTime.class, LocalDateTime.now());
        // updateTime强制覆盖，确保每次插入时都是最新时间
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

    /*
     * 方法功能:更新时自动填充updateTime
     * 参数:metaObject-MyBatis-Plus元对象
     * 返回数据:无
     * */
    @Override
    public void updateFill(MetaObject metaObject) {
        // updateTime强制覆盖，确保每次更新时都是最新时间
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
