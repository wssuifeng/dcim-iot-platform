package com.dcim.simulator.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 室内热湿状态（环境物理引擎持久化状态）：每机房一行
 *
 * 重启后从 last_tick_at 续积分，绝不重置——设备发热/空调制冷的影响因此持久。
 */
@Getter
@Setter
@Entity
@Table(name = "room_environment_state")
public class RoomEnvironmentState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private Long profileId;

    // 室内温度 ℃
    @Column(name = "indoor_temp", nullable = false)
    private double indoorTemp;

    // 室内相对湿度 %
    @Column(name = "indoor_rh", nullable = false)
    private double indoorRh;

    // 空调压缩机运行位（回差控制）
    @Column(name = "ac_running", nullable = false)
    private boolean acRunning;

    // 上次积分时刻（断档补算起点）
    @Column(name = "last_tick_at", nullable = false)
    private LocalDateTime lastTickAt;
}
