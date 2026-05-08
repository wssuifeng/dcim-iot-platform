package com.example.platform.common.exception;

public class DeviceTypeNotFound extends BusinessException{
    public DeviceTypeNotFound(Integer typeId) {
        super(4001,"设备类型id不存在:"+typeId);
    }

}
