package com.example.platform.service;

import java.util.Set;

/**
 * BIZ-OPEN-02 设备指标业务契约常量。
 */
public final class DevicePointMetricContract {

    public static final String UPS = "UPS";
    public static final String PDU_METER = "PDU_METER";
    public static final String PRECISION_AC = "PRECISION_AC";
    public static final String TEMP_HUMIDITY = "TEMP_HUMIDITY";
    public static final String LEAK_DETECTOR = "LEAK_DETECTOR";

    public static final Set<String> FIVE_DEVICE_TYPES = Set.of(
            UPS, PDU_METER, PRECISION_AC, TEMP_HUMIDITY, LEAK_DETECTOR);
    public static final Set<String> POINT_DATA_TYPES = Set.of("NUMBER", "BOOLEAN", "STRING");

    public static final int DEFAULT_RETENTION_DAYS = 90;
    public static final int MAX_RETENTION_DAYS = 90;

    private DevicePointMetricContract() {
    }

    public static boolean isSupportedDeviceType(String typeCode) {
        return typeCode != null && FIVE_DEVICE_TYPES.contains(typeCode.trim().toUpperCase());
    }
}
