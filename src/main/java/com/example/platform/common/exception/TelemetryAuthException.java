package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:TB 兼容遥测通道鉴权失败异常（ADR-008 D2：无效 token 统一 HTTP 401 防枚举）
 * */
public class TelemetryAuthException extends BusinessException {

    /*
     * 方法功能:构造遥测鉴权失败异常
     * 参数:无
     * 返回数据:无
     * */
    public TelemetryAuthException() {
        super(ResultCode.UNAUTHORIZED, "遥测 token 无效");
    }
}
