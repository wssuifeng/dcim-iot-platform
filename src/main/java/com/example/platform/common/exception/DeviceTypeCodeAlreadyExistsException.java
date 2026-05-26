package com.example.platform.common.exception;

/*
 * 类功能:设备类型编码已存在异常，当新增或更新设备类型时编码与已有类型重复时抛出
 * */
public class DeviceTypeCodeAlreadyExistsException extends RuntimeException {

    /*
     * 方法功能:根据重复的类型编码构造异常
     * 参数:typeCode-重复的设备类型编码
     * 返回数据:无
     * */
    public DeviceTypeCodeAlreadyExistsException(String typeCode) {
        super("设备类型编码已存在: " + typeCode);
    }

    /*
     * 方法功能:获取业务错误码
     * 参数:无
     * 返回数据:int-错误码4051
     * */
    public int getCode() {
        return 4051;
    }
}
