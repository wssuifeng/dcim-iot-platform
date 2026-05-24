package com.example.platform.common.exception;

public class DeviceTypeCodeAlreadyExistsException extends RuntimeException {

    private final int code = 4051;

    public DeviceTypeCodeAlreadyExistsException(String typeCode) {
        super("设备类型编码已存在: " + typeCode);
    }

    public int getCode() {
        return code;
    }
}
