package com.example.platform.common;

import java.util.Arrays;
import java.util.Optional;

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

    // 文件/导入导出模块 406x
    public static final int FILE_EMPTY = 4061;
    public static final int FILE_FORMAT_ERROR = 4062;
    public static final int IMPORT_SIZE_EXCEED = 4063;
    public static final int IMPORT_PARTIAL_FAIL = 4064;
    public static final int EXPORT_SIZE_EXCEED = 4065;
    public static final int EXPORT_ERROR = 4066;

    // 数据范围模块 407x
    public static final int DATA_SCOPE_ACCESS_DENIED = 4071;
    public static final int DATA_SCOPE_NOT_ASSIGNED = 4072;

    // 状态机模块 408x
    public static final int ALARM_STATE_INVALID_TRANSITION = 4081;
    public static final int ALARM_STATE_CONFLICT = 4082;

    // 模拟端模块 409x
    public static final int SIMULATION_WRITE_DISABLED = 4091;
    public static final int SIMULATION_SCENE_NOT_FOUND = 4092;

    // 限流模块 429x
    public static final int RATE_LIMITED = 4290;

    // 审计模块 430x
    public static final int AUDIT_RECORD_FAILED = 4301;

    // WebSocket 模块 440x
    public static final int WEBSOCKET_UNAUTHORIZED = 4401;
    public static final int WEBSOCKET_ORIGIN_NOT_ALLOWED = 4402;

    // 功能开关模块 450x
    public static final int FEATURE_DISABLED = 4501;

    // 供应链和生产门控模块 460x
    public static final int SUPPLY_CONFIG_UNSAFE = 4601;

    // 系统配置模块 500x
    public static final int SYSTEM_CONFIG_INVALID = 5002;

    // 第四阶段稳定错误码前缀，供业务契约和前端展示映射使用
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    public static final String AUTHZ_FORBIDDEN = "AUTHZ_FORBIDDEN";
    public static final String SCOPE_ACCESS_DENIED = "SCOPE_ACCESS_DENIED";
    public static final String VALIDATION_INVALID_PARAMETER = "VALIDATION_INVALID_PARAMETER";
    public static final String STATE_INVALID_TRANSITION = "STATE_INVALID_TRANSITION";
    public static final String SIM_WRITE_DISABLED = "SIM_WRITE_DISABLED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String AUDIT_WRITE_FAILED = "AUDIT_WRITE_FAILED";
    public static final String WS_UNAUTHORIZED = "WS_UNAUTHORIZED";
    public static final String WS_ORIGIN_NOT_ALLOWED = "WS_ORIGIN_NOT_ALLOWED";
    public static final String FEATURE_SWITCH_DISABLED = "FEATURE_DISABLED";
    public static final String SUPPLY_UNSAFE_CONFIG = "SUPPLY_UNSAFE_CONFIG";
    public static final String SYSTEM_INTERNAL_ERROR = "SYSTEM_INTERNAL_ERROR";

    public enum StableError {
        AUTH_UNAUTHORIZED(ResultCode.AUTH_UNAUTHORIZED, ResultCode.UNAUTHORIZED, "未认证或Token无效"),
        AUTH_INVALID_CREDENTIALS(ResultCode.AUTH_INVALID_CREDENTIALS, ResultCode.INVALID_CREDENTIALS, "账号或密码错误"),
        AUTHZ_FORBIDDEN(ResultCode.AUTHZ_FORBIDDEN, ResultCode.FORBIDDEN, "权限不足"),
        SCOPE_ACCESS_DENIED(ResultCode.SCOPE_ACCESS_DENIED, ResultCode.DATA_SCOPE_ACCESS_DENIED, "数据范围拒绝访问"),
        VALIDATION_INVALID_PARAMETER(ResultCode.VALIDATION_INVALID_PARAMETER, ResultCode.INVALID_PARAMETER, "参数校验失败"),
        STATE_INVALID_TRANSITION(ResultCode.STATE_INVALID_TRANSITION, ResultCode.ALARM_STATE_INVALID_TRANSITION, "状态流转不合法"),
        SIM_WRITE_DISABLED(ResultCode.SIM_WRITE_DISABLED, ResultCode.SIMULATION_WRITE_DISABLED, "模拟写入已禁用"),
        RATE_LIMIT_EXCEEDED(ResultCode.RATE_LIMIT_EXCEEDED, ResultCode.RATE_LIMITED, "请求过于频繁"),
        AUDIT_WRITE_FAILED(ResultCode.AUDIT_WRITE_FAILED, ResultCode.AUDIT_RECORD_FAILED, "审计记录写入失败"),
        WS_UNAUTHORIZED(ResultCode.WS_UNAUTHORIZED, ResultCode.WEBSOCKET_UNAUTHORIZED, "WebSocket未认证"),
        WS_ORIGIN_NOT_ALLOWED(ResultCode.WS_ORIGIN_NOT_ALLOWED, ResultCode.WEBSOCKET_ORIGIN_NOT_ALLOWED, "WebSocket Origin不被允许"),
        FEATURE_SWITCH_DISABLED(ResultCode.FEATURE_SWITCH_DISABLED, ResultCode.FEATURE_DISABLED, "功能开关已关闭"),
        SUPPLY_UNSAFE_CONFIG(ResultCode.SUPPLY_UNSAFE_CONFIG, ResultCode.SUPPLY_CONFIG_UNSAFE, "生产安全配置不合规"),
        SYSTEM_INTERNAL_ERROR(ResultCode.SYSTEM_INTERNAL_ERROR, ResultCode.INTERNAL_SERVER_ERROR, "系统内部错误");

        private final String code;
        private final int legacyCode;
        private final String defaultMessage;

        StableError(String code, int legacyCode, String defaultMessage) {
            this.code = code;
            this.legacyCode = legacyCode;
            this.defaultMessage = defaultMessage;
        }

        public String getCode() {
            return code;
        }

        public int getLegacyCode() {
            return legacyCode;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    public static Optional<StableError> findStableError(String stableCode) {
        return Arrays.stream(StableError.values())
                .filter(error -> error.getCode().equals(stableCode))
                .findFirst();
    }

    public static int legacyCodeOf(String stableCode) {
        return findStableError(stableCode)
                .map(StableError::getLegacyCode)
                .orElse(INTERNAL_SERVER_ERROR);
    }

    private ResultCode() {
    }
}
