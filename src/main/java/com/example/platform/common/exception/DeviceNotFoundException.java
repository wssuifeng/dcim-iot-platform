package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:设备未找到异常，当根据ID查询设备不存在时抛出
 * */
public class DeviceNotFoundException extends BusinessException {

    /*
     * 方法功能:根据设备ID构造异常
     * 参数:id-未找到的设备ID
     * 返回数据:无
     * */
    public DeviceNotFoundException(Integer id) {
        super(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + id);
    }
}
