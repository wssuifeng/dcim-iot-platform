package com.dcim.simulator.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 虚拟设备：以 Modbus TCP slave 形态对外暴露寄存器，device_code 对应平台 devices.device_code
 */
@Getter
@Setter
@Entity
@Table(name = "virtual_device")
public class VirtualDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 平台设备编码（经 PlatformAdminClient 创建后回填） */
    @Column(name = "device_code", nullable = false, unique = true, length = 30)
    private String deviceCode;

    /** 设备显示名（与平台 devices.device_name 对齐） */
    @Column(name = "device_name", length = 50)
    private String deviceName;

    /** 设备类型ID（与平台 device_types 对齐：1-UPS 2-PDU_METER 3-PRECISION_AC 4-TEMP_HUMIDITY 5-LEAK_DETECTOR） */
    @Column(name = "type_id")
    private Integer typeId;

    /** 位置描述（与平台 devices.location 对齐） */
    @Column(length = 100)
    private String location;

    @Column(name = "cloud_room_id", nullable = false)
    private Long cloudRoomId;

    /** 本机 Modbus slave 监听端口 */
    @Column(name = "modbus_port", nullable = false, unique = true)
    private Integer modbusPort;

    @Column(name = "sample_interval_sec", nullable = false)
    private Integer sampleIntervalSec = 1;

    /** 传感器噪声幅度（±该值内均匀分布，种子化） */
    @Column(name = "noise_amplitude")
    private Double noiseAmplitude = 0.0;

    /** 故障模式：NONE / SENSOR_STUCK / SENSOR_DEAD */
    @Column(name = "fault_mode", nullable = false, length = 20)
    private String faultMode = "NONE";

    /** 0-运行 1-暂停 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
