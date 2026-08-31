package com.dcim.simulator.scenario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 剧本步骤：delay 后把某环境通道改写为指定函数（action=SET_CHANNEL）
 */
@Getter
@Setter
@Entity
@Table(name = "scenario_step")
public class ScenarioStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** 相对剧本开始的延迟毫秒 */
    @Column(name = "delay_ms", nullable = false)
    private Integer delayMs;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "point_code", nullable = false, length = 50)
    private String pointCode;

    /** CONSTANT/SINUSOID/RAMP/STEP */
    @Column(name = "func_type", length = 20)
    private String funcType;

    /** JSON 参数：{"baseValue":..,"amplitude":..,"periodSec":..,"slopePerSec":..,"stepTo":..} */
    @Column(length = 500)
    private String params;

    @Column(length = 300)
    private String description;
}
