package com.example.platform.common;

import lombok.Data;

/*
 * 类功能:统一响应包装类，所有接口返回值均使用此类包装，保证前端接收格式一致
 * */
@Data
public class Result<T> {

    /** 状态码（200=成功，400=参数错误，404=资源未找到，500=服务器异常） */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /*
     * 方法功能:构建成功响应（无数据）
     * 参数:无
     * 返回数据:Result<Void>-成功响应对象
     * */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        return result;
    }

    /*
     * 方法功能:构建成功响应（携带数据）
     * 参数:data-响应数据
     * 返回数据:Result<T>-携带数据的成功响应对象
     * */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /*
     * 方法功能:构建失败响应（仅消息，默认500状态码）
     * 参数:message-错误消息
     * 返回数据:Result<T>-失败响应对象
     * */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /*
     * 方法功能:构建失败响应（自定义状态码和消息）
     * 参数:code-错误码, message-错误消息
     * 返回数据:Result<T>-失败响应对象
     * */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
