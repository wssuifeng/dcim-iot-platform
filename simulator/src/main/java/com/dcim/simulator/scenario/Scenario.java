package com.dcim.simulator.scenario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 剧本：SET_CHANNEL 事件序列的环境编排载体
 */
@Getter
@Setter
@Entity
@Table(name = "scenario")
public class Scenario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_code", nullable = false, unique = true, length = 50)
    private String scenarioCode;

    @Column(name = "scenario_name", nullable = false, length = 100)
    private String scenarioName;

    @Column(name = "cloud_room_id", nullable = false)
    private Long cloudRoomId;

    @Column(length = 500)
    private String description;
}
