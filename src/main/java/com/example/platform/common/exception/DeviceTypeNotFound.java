package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:设备类型未找到异常，当根据ID查询设备类型不存在时抛出
 * */
public class DeviceTypeNotFound extends BusinessException {

    /*
     * 方法功能:根据设备类型ID构造异常
     * 参数:id-未找到的设备类型ID
     * 返回数据:无
     * */
    public DeviceTypeNotFound(Integer id) {
        super(ResultCode.DEVICE_TYPE_NOT_FOUND, "设备类型不存在: " + id);
    }
}
