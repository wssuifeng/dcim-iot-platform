package com.example.platform.common;

/*
 * 类功能:统一响应错误码常量类，消除代码中硬编码的魔法数字
 * */
public final class ResultCode {

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;

    public static final int DEVICE_TYPE_NOT_FOUND = 4001;
    public static final int DEVICE_CODE_EXISTS = 4002;
    public static final int DEVICE_NOT_FOUND = 4003;
    public static final int USER_NOT_FOUND = 4004;
    public static final int USERNAME_ALREADY_EXISTS = 4005;
    public static final int ACCOUNT_LOCKED = 4006;
    public static final int ACCOUNT_DISABLED = 4007;
    public static final int INVALID_CREDENTIALS = 4008;

    private ResultCode() {
    }
}
