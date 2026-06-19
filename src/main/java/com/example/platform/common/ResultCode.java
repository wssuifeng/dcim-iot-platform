package com.example.platform.common;

/*
 * 类功能:统一响应错误码常量类，消除代码中硬编码的魔法数字
 * */
public final class ResultCode {

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;

    // 通用错误码 400x~500x
    public static final int INVALID_PARAMETER = 4001;
    public static final int UNAUTHORIZED = 4010;
    public static final int FORBIDDEN = 4030;
    public static final int RESOURCE_NOT_FOUND = 4040;
    public static final int METHOD_NOT_ALLOWED = 4050;
    public static final int INTERNAL_SERVER_ERROR = 5000;

    // 安全模块 401x
    public static final int USER_NOT_FOUND = 4011;
    public static final int USERNAME_ALREADY_EXISTS = 4012;
    public static final int ACCOUNT_LOCKED = 4013;
    public static final int ACCOUNT_DISABLED = 4014;
    public static final int INVALID_CREDENTIALS = 4015;

    // 设备模块 402x
    public static final int DEVICE_TYPE_NOT_FOUND = 4021;
    public static final int DEVICE_CODE_EXISTS = 4022;
    public static final int DEVICE_NOT_FOUND = 4023;

    // 数据模块 403x
    public static final int DATA_TIME_RANGE_EXCEED = 4031;
    public static final int DATA_VALUE_OUT_OF_RANGE = 4032;

    // 告警模块 404x
    public static final int ALERT_NOT_FOUND = 4041;
    public static final int ALERT_HANDLED = 4042;

    // 配置模块 405x
    public static final int CONFIG_NOT_FOUND = 4051;

    private ResultCode() {
    }
}
