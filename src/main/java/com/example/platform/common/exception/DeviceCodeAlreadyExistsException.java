package com.example.platform.common.exception;

/*
 * 类功能:设备编码已存在异常，当新增设备的编码与已有设备重复时抛出
 * */
public class DeviceCodeAlreadyExistsException extends BusinessException {

    /*
     * 方法功能:根据设备编码构造异常
     * 参数:deviceCode-重复的设备编码
     * 返回数据:无
     * */
    public DeviceCodeAlreadyExistsException(String deviceCode) {
        super(4002, "设备编码已存在: " + deviceCode);
    }
}
