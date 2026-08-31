package com.dcim.simulator.gateway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 网关配置：南向轮询与北向上报的运行参数（单行配置，管理页可改）
 */
@Getter
@Setter
@Entity
@Table(name = "gateway_config")
public class GatewayConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 北向传输：MQTT / HTTP */
    @Column(name = "northbound_transport", nullable = false, length = 10)
    private String northboundTransport = "MQTT";

    @Column(name = "platform_base_url", nullable = false, length = 200)
    private String platformBaseUrl;

    /** 平台 ingest_credential（simulated=1 的仿真专用凭证） */
    @Column(name = "ingest_access_key", nullable = false, length = 64)
    private String ingestAccessKey;

    @Column(name = "ingest_secret", nullable = false, length = 100)
    private String ingestSecret;

    /** 绑定的平台机房 ID：一机房一网关（旧数据启动时按设备归属回填） */
    @Column(name = "cloud_room_id")
    private Long cloudRoomId;

    /** 平台管理账号（登录换 JWT，供设备准备流程） */
    @Column(name = "admin_username", nullable = false, length = 50)
    private String adminUsername;

    @Column(name = "admin_password", nullable = false, length = 100)
    private String adminPassword;

    @Column(name = "poll_interval_sec", nullable = false)
    private Integer pollIntervalSec = 5;

    @Column(name = "report_interval_sec", nullable = false)
    private Integer reportIntervalSec = 60;

    /** ON_CHANGE 死区：|当前值-上次上报值| > deadband 才上报 */
    @Column(name = "change_deadband")
    private BigDecimal changeDeadband = new BigDecimal("0.5");

    /** 越限时加密上报间隔 */
    @Column(name = "alarm_report_interval_sec", nullable = false)
    private Integer alarmReportIntervalSec = 5;

    /** 0-运行 1-暂停 */
    @Column(nullable = false)
    private Integer status = 0;
}
