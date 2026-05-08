package com.example.platform.common.exception;

public class DeviceNotFoundException extends BusinessException{
    public DeviceNotFoundException(String deviceName){
        super(4003,"设备未找到:"+deviceName);
    }
}
