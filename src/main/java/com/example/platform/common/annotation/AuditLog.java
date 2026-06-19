package com.example.platform.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * 类功能:操作审计日志注解，标注在 Controller 方法上，由 AOP 自动记录审计日志
 * */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /*
     * 操作类型，建议采用大写下划线格式，如 CREATE_USER、DELETE_DEVICE
     * */
    String operation();

    /*
     * 操作详情模板，支持 SpEL 表达式，如 "#userId"
     * 默认为空字符串，表示不记录额外详情
     * */
    String detail() default "";
}
