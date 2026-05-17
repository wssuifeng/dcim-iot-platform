package com.example.platform.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    // 数据插入时如果为空则自动填入
    public void insertFill(MetaObject metaObject) {
        // 采集时间
        this.strictInsertFill(metaObject, "collectTime", LocalDateTime.class, LocalDateTime.now());
        // 创建时间
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        // 更新时间
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
