-- ============================================================
-- dcim-simulator schema（MySQL，与平台同库实例；阶段七起仿真器统一 MySQL）
-- 六张核心表 + 剧本两表；幂等重启（IF NOT EXISTS）
-- ============================================================

CREATE TABLE IF NOT EXISTS virtual_device (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_code     VARCHAR(30) NOT NULL UNIQUE,
    device_name     VARCHAR(50),
    type_id         INT,
    location        VARCHAR(100),
    cloud_room_id   BIGINT NOT NULL,
    modbus_port     INT NOT NULL UNIQUE,
    sample_interval_sec INT NOT NULL DEFAULT 1,
    noise_amplitude DECIMAL(10,4) DEFAULT 0,
    fault_mode      VARCHAR(20) NOT NULL DEFAULT 'NONE',
    status          TINYINT NOT NULL DEFAULT 0,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_register_map (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id     BIGINT NOT NULL,
    point_code    VARCHAR(50) NOT NULL,
    register_addr INT NOT NULL,
    scale_factor  DECIMAL(10,6) NOT NULL DEFAULT 10,
    CONSTRAINT uk_dev_reg UNIQUE (device_id, register_addr)
);

CREATE TABLE IF NOT EXISTS room_environment_profile (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    cloud_room_id BIGINT NOT NULL UNIQUE,
    seed          BIGINT NOT NULL DEFAULT 42,
    -- 环境物理引擎：机房参数（V2 新增；阶段七转 MySQL 时并入建表定义）
    it_load_kw     DECIMAL(10,2) DEFAULT 40,
    ac_rated_kw    DECIMAL(10,2) DEFAULT 60,
    ac_setpoint_c  DECIMAL(5,2)  DEFAULT 24,
    ac_setpoint_rh DECIMAL(5,2)  DEFAULT 55,
    ac_enabled     TINYINT       DEFAULT 1
);


-- 环境物理引擎：室内热湿状态（持久化，重启续积分，永不重置）
CREATE TABLE IF NOT EXISTS room_environment_state (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id   BIGINT NOT NULL UNIQUE,
    indoor_temp  DECIMAL(8,4) NOT NULL,
    indoor_rh    DECIMAL(6,4) NOT NULL,
    ac_running   TINYINT NOT NULL DEFAULT 0,
    last_tick_at DATETIME NOT NULL,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS environment_channel (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id    BIGINT NOT NULL,
    point_code    VARCHAR(50) NOT NULL,
    func_type     VARCHAR(20) NOT NULL DEFAULT 'CONSTANT',
    base_value    DECIMAL(10,4) NOT NULL,
    amplitude     DECIMAL(10,4) DEFAULT 0,
    period_sec    INT DEFAULT 86400,
    slope_per_sec DECIMAL(10,6) DEFAULT 0,
    step_at       DATETIME,
    step_to       DECIMAL(10,4),
    duration_sec  INT,
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gateway_config (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(100) NOT NULL,
    northbound_transport VARCHAR(10) NOT NULL DEFAULT 'MQTT',
    platform_base_url   VARCHAR(200) NOT NULL,
    ingest_access_key   VARCHAR(64) NOT NULL,
    ingest_secret       VARCHAR(100) NOT NULL,
    -- 已废弃：网关不再持有云平台管理账号（历史库兼容保留可空列）
    admin_username      VARCHAR(50),
    admin_password      VARCHAR(100),
    -- 绑定的平台机房 ID：一机房一网关（阶段七多实例化；旧数据启动时回填）
    cloud_room_id       BIGINT,
    poll_interval_sec   INT NOT NULL DEFAULT 5,
    report_interval_sec INT NOT NULL DEFAULT 60,
    change_deadband     DECIMAL(10,4) DEFAULT 0.5,
    alarm_report_interval_sec INT NOT NULL DEFAULT 5,
    status              TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS scenario (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(50) NOT NULL UNIQUE,
    scenario_name VARCHAR(100) NOT NULL,
    cloud_room_id BIGINT NOT NULL,
    description  VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS scenario_step (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_id BIGINT NOT NULL,
    step_order  INT NOT NULL,
    delay_ms    INT NOT NULL,
    action      VARCHAR(30) NOT NULL,
    point_code  VARCHAR(50) NOT NULL,
    func_type   VARCHAR(20),
    params      VARCHAR(500),
    description VARCHAR(300)
);
