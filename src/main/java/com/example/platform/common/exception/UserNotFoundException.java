package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:用户未找到异常，当根据ID或用户名查询用户不存在时抛出
 * */
public class UserNotFoundException extends BusinessException {

    /*
     * 方法功能:根据用户ID构造异常
     * 参数:id-未找到的用户ID
     * 返回数据:无
     * */
    public UserNotFoundException(Long id) {
        super(ResultCode.USER_NOT_FOUND, "用户不存在: " + id);
    }

    /*
     * 方法功能:根据用户名构造异常
     * 参数:username-未找到的用户名
     * 返回数据:无
     * */
    public UserNotFoundException(String username) {
        super(ResultCode.USER_NOT_FOUND, "用户不存在: " + username);
    }
}