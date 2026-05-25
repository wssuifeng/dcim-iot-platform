package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:账号锁定异常，连续5次登录失败后账号被锁定30分钟时抛出
 * */
public class AccountLockedException extends BusinessException {

    /*
     * 方法功能:构造账号锁定异常
     * 参数:lockTime-锁定截止时间
     * 返回数据:无
     * */
    public AccountLockedException(String lockTime) {
        super(ResultCode.ACCOUNT_LOCKED, "账号已被锁定，请于 " + lockTime + " 后重试");
    }
}