package com.dcim.simulator.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 环境通道：纯函数求值的一项（CONSTANT/SINUSOID/RAMP/STEP），同指标多通道求和
 */
@Getter
@Setter
@Entity
@Table(name = "environment_channel")
public class EnvironmentChannel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "point_code", nullable = false, length = 50)
    private String pointCode;

    /** CONSTANT / SINUSOID / RAMP / STEP */
    @Column(name = "func_type", nullable = false, length = 20)
    private String funcType = "CONSTANT";

    @Column(name = "base_value", nullable = false)
    private BigDecimal baseValue = BigDecimal.ZERO;

    /** SINUSOID 振幅 */
    @Column(name = "amplitude")
    private BigDecimal amplitude = BigDecimal.ZERO;

    /** SINUSOID 周期（默认 24h 日夜循环） */
    @Column(name = "period_sec")
    private Integer periodSec = 86400;

    /** RAMP 斜率（工程量/秒），以 update_time 为 t0 */
    @Column(name = "slope_per_sec")
    private BigDecimal slopePerSec = BigDecimal.ZERO;

    /** STEP 阶跃时刻 */
    @Column(name = "step_at")
    private LocalDateTime stepAt;

    /** STEP 目标值（本通道贡献在 step_at 后从 base 切换为 step_to） */
    @Column(name = "step_to")
    private BigDecimal stepTo;

    /** TRANSITION 过渡时长（秒）：base 平滑过渡到 step_to */
    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
