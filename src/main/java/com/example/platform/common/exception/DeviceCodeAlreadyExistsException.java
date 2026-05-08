package com.example.platform.common.exception;

public class DeviceCodeAlreadyExistsException extends BusinessException{
    public DeviceCodeAlreadyExistsException(String deviceCode){
        super(4002,"设备代码已存在:"+deviceCode);
    }
}
