package com.dcim.simulator.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 机房环境档案：每机房一份，持有确定性随机种子与环境物理引擎参数
 *
 * 参数列由 schema 增量补齐，旧库行可能为 NULL——读取侧走 XXXOf() 空安全方法取默认值。
 */
@Getter
@Setter
@Entity
@Table(name = "room_environment_profile")
public class RoomEnvironmentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cloud_room_id", nullable = false, unique = true)
    private Long cloudRoomId;

    @Column(name = "seed", nullable = false)
    private Long seed = 42L;

    // IT 负载（服务器发热）kW——室内持续热源
    @Column(name = "it_load_kw")
    private Double itLoadKw;

    // 空调额定制冷量 kW
    @Column(name = "ac_rated_kw")
    private Double acRatedKw;

    // 空调温度设定 ℃（±0.5 回差）
    @Column(name = "ac_setpoint_c")
    private Double acSetpointC;

    // 空调湿度设定 %
    @Column(name = "ac_setpoint_rh")
    private Double acSetpointRh;

    // 空调总开关（停机演练置 false）
    @Column(name = "ac_enabled")
    private Boolean acEnabled;

    public double itLoadKwOf() {
        return itLoadKw == null ? 40.0 : itLoadKw;
    }

    public double acRatedKwOf() {
        return acRatedKw == null ? 60.0 : acRatedKw;
    }

    public double acSetpointCOf() {
        return acSetpointC == null ? 24.0 : acSetpointC;
    }

    public double acSetpointRhOf() {
        return acSetpointRh == null ? 55.0 : acSetpointRh;
    }

    public boolean acEnabledOf() {
        return acEnabled == null || acEnabled;
    }
}
