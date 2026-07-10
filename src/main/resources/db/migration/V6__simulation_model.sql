-- ============================================================
-- Flyway V6: 模拟端模型（BIZ-TRAE-01）
--
-- 契约依据：共享契约 03-共享契约.md §6 模拟端契约
--   模拟端必须由后端服务生成事实数据，前端和脚本不能直接伪造核心业务事实。
--   模型：SimulationScene / SimulationSceneStep / SimulationRunRecord / SimulationGeneratedDataRef
--
-- 范围字段：organization_id、cloud_room_id 用于服务层数据范围校验，
--   不依赖 Device.location。所有查询必须经 DeviceScope/DataScope 过滤。
--
-- 生产门控：写入由 simulation.write-enabled 开关控制，由 ProductionSafetyGuard 阻断生产启用。
--   此处仅建表结构，开关校验在 Service 层完成。
--
-- 数据可追溯：simulation_generated_data_ref 引用 metric_sample.id（不建立强制外键，
--   避免大规模历史事实表外键约束），保留策略由 MetricRetentionPolicy 统一回收。
-- ============================================================

-- 1. 模拟场景定义
CREATE TABLE IF NOT EXISTS simulation_scene (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '场景ID（主键）',
    scene_code      VARCHAR(50)  NOT NULL                COMMENT '场景编码（同组织内唯一）',
    scene_name      VARCHAR(100) NOT NULL                COMMENT '场景名称',
    description     VARCHAR(500)                         COMMENT '场景描述',
    organization_id BIGINT       NOT NULL                COMMENT '所属组织ID（数据范围校验依据）',
    cloud_room_id   BIGINT                                COMMENT '所属机房ID（可空，表示跨机房场景）',
    device_type_code VARCHAR(50)                         COMMENT '目标设备类型编码（参考 DevicePointMetricContract 五类设备）',
    status          TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0-启用 1-停用',
    created_by      BIGINT                               COMMENT '创建用户ID',
    create_time     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sim_scene_org_code (organization_id, scene_code),
    KEY idx_sim_scene_org  (organization_id),
    KEY idx_sim_scene_room (cloud_room_id),
    KEY idx_sim_scene_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟场景定义';

-- 2. 模拟场景步骤
CREATE TABLE IF NOT EXISTS simulation_scene_step (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '步骤ID（主键）',
    scene_id        BIGINT       NOT NULL                COMMENT '所属场景ID',
    step_order      INT          NOT NULL                COMMENT '步骤顺序（从1开始，递增）',
    point_id        BIGINT       NOT NULL                COMMENT '目标点位ID（关联 point_definition）',
    target_device_id INT                                 COMMENT '目标设备ID（关联 devices，为空表示运行时由调用方指定）',
    sample_value    DECIMAL(10,2)                        COMMENT '步骤注入的采样值',
    action          VARCHAR(20)  NOT NULL DEFAULT 'WRITE_SAMPLE' COMMENT '步骤动作：WRITE_SAMPLE/SLEEP',
    delay_ms        INT          NOT NULL DEFAULT 0      COMMENT '执行前延迟毫秒（调度节流）',
    description     VARCHAR(200)                         COMMENT '步骤描述',
    create_time     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_sim_step_scene  (scene_id),
    KEY idx_sim_step_order  (scene_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟场景步骤';

-- 3. 模拟运行记录
CREATE TABLE IF NOT EXISTS simulation_run_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '运行ID（主键）',
    scene_id        BIGINT       NOT NULL                COMMENT '关联场景ID',
    scene_snapshot  TEXT                                 COMMENT '运行时场景快照（JSON，便于复现）',
    trigger_user_id BIGINT       NOT NULL                COMMENT '触发用户ID',
    organization_id BIGINT       NOT NULL                COMMENT '所属组织ID（数据范围校验依据）',
    cloud_room_id   BIGINT                                COMMENT '所属机房ID（可空）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    total_steps     INT          NOT NULL DEFAULT 0      COMMENT '步骤总数',
    success_steps   INT          NOT NULL DEFAULT 0      COMMENT '成功步骤数',
    error_message   VARCHAR(500)                          COMMENT '失败原因（可为空）',
    start_time      DATETIME                             COMMENT '开始执行时间',
    end_time        DATETIME                             COMMENT '结束执行时间',
    create_time     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sim_run_scene  (scene_id),
    KEY idx_sim_run_org    (organization_id),
    KEY idx_sim_run_room   (cloud_room_id),
    KEY idx_sim_run_status (status),
    KEY idx_sim_run_trigger (trigger_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟运行记录';

-- 4. 模拟生成数据引用（无逻辑删除：必须保留可追溯事实；按 metric retire policy 物理清理）
CREATE TABLE IF NOT EXISTS simulation_generated_data_ref (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '引用ID（主键）',
    run_id            BIGINT       NOT NULL                COMMENT '关联运行ID',
    step_id           BIGINT                                COMMENT '关联步骤ID（可空，用于 RUN 级聚合场景）',
    device_id         INT          NOT NULL                COMMENT '生成数据目标设备ID',
    point_id          BIGINT       NOT NULL                COMMENT '生成数据目标点位ID',
    metric_sample_id  BIGINT                                COMMENT '写入 metric_sample 的主键（可追溯依据）',
    action            VARCHAR(20)  NOT NULL DEFAULT 'WRITE_SAMPLE' COMMENT '动作：WRITE_SAMPLE/SLEEP',
    sample_value      DECIMAL(10,2)                        COMMENT '注入的采样值',
    generated_at      DATETIME     NOT NULL                COMMENT '生成时间',
    create_time       DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sim_gendata_run     (run_id),
    KEY idx_sim_gendata_step    (step_id),
    KEY idx_sim_gendata_sample  (metric_sample_id),
    KEY idx_sim_gendata_device  (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟生成数据引用（生成数据可追溯到运行记录）';