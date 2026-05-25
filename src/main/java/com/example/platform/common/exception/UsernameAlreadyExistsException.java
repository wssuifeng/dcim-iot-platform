package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:用户名已存在异常，创建用户时用户名已被占用抛出
 * */
public class UsernameAlreadyExistsException extends BusinessException {

    /*
     * 方法功能:根据重复的用户名构造异常
     * 参数:username-已存在的用户名
     * 返回数据:无
     * */
    public UsernameAlreadyExistsException(String username) {
        super(ResultCode.USERNAME_ALREADY_EXISTS, "用户名已存在: " + username);
    }
}