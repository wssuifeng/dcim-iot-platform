package com.dcim.simulator.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 寄存器映射：pointCode → 保持寄存器地址 + 缩放系数（工程量 = raw / scale）
 */
@Getter
@Setter
@Entity
@Table(name = "device_register_map")
public class RegisterMapEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 平台 point_definition.point_code */
    @Column(name = "point_code", nullable = false, length = 50)
    private String pointCode;

    /** 保持寄存器地址（0 起，对应 40001） */
    @Column(name = "register_addr", nullable = false)
    private Integer registerAddr;

    /** 缩放系数：工程量 = raw / scale（存 10 即寄存器值 ÷10 还原） */
    @Column(name = "scale_factor", nullable = false)
    private BigDecimal scaleFactor = BigDecimal.TEN;
}
