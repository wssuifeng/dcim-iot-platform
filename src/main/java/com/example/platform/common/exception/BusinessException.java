package com.example.platform.common.exception;

import lombok.Getter;

/*
 * 类功能:通用业务异常基类，携带错误码和错误消息，供子类继承实现具体业务异常
 * */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    /*
     * 方法功能:仅指定消息构造业务异常，错误码默认为400
     * 参数:message-错误消息
     * 返回数据:无
     * */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /*
     * 方法功能:指定错误码和消息构造业务异常
     * 参数:code-业务错误码, message-错误消息
     * 返回数据:无
     * */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
