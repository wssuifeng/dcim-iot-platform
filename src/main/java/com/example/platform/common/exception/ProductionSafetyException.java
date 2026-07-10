package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:生产环境安全门控异常，用于启动期阻断不安全默认配置
 * */
public class ProductionSafetyException extends BusinessException {

    public ProductionSafetyException(String message) {
        super(ResultCode.SUPPLY_CONFIG_UNSAFE, message);
    }
}
