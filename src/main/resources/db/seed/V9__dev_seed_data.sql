-- ============================================================
-- Flyway seed V9: 开发环境基础测试数据（仅 dev profile 加载）
--
-- 加载方式：application-dev.yaml 中 spring.flyway.locations 追加 classpath:db/seed；
--   prod 不包含该 location，生产库不会注入任何演示数据。
-- 版本号取 V9（排在 db/migration V1-V8 之后执行）；文档原写 V1 会与 migration/V1
--   产生 Flyway 多 location 版本冲突，此处为修正。
--
-- 内容：跑通"上报→评估→告警→通知"全链的最小数据集。
--   固定主键写入，便于外键引用与测试断言；密码统一 Admin123。
--   metric_sample / alarm_event 不注入，由运行时上报与仿真产生。
--   V10 用户域收束后：无组织/成员/范围种子，机房为顶层资源。
-- ============================================================

-- ==================== 1. 用户（ID=1 平台管理员为测试前置依赖） ====================
INSERT INTO sys_user (id, username, password, nickname, role, email, email_verified, status) VALUES
    (1, 'admin',    '$2a$10$Dbl.51QQE5/8jgtp1pZro.SQae4Cpglu0VE7IVFLj2GSgKnxBF4Gu', '系统管理员', 'ADMIN',    'admin@demo.local',    1, 0),
    (2, 'operator', '$2a$10$Dbl.51QQE5/8jgtp1pZro.SQae4Cpglu0VE7IVFLj2GSgKnxBF4Gu', '演示运维员', 'OPERATOR', 'operator@demo.local', 1, 0),
    (3, 'viewer',   '$2a$10$Dbl.51QQE5/8jgtp1pZro.SQae4Cpglu0VE7IVFLj2GSgKnxBF4Gu', '演示只读用户', 'VIEWER', 'viewer@demo.local',   1, 0);

-- ==================== 2. 占位组织与机房（顶层资源） ====================
-- organization_id=999 占位：seed 先于 V10 执行，此时列仍存在且 NOT NULL 并有指向
-- sys_organization 的外键，故先插 id=999 占位组织满足约束；V10 删表删列时一并清除
-- （不用 id=0：AUTO_INCREMENT 列的 0 会被 MySQL 当作自增哨兵值处理）
INSERT INTO sys_organization (id, org_name, org_code, description) VALUES
    (999, '占位组织（V10 删除）', 'PLACEHOLDER', 'seed 占位，随 V10 用户域收束删除');
INSERT INTO sys_cloud_room (id, room_name, room_code, location, organization_id) VALUES
    (1, '演示机房A', 'DEMO_ROOM_A', '一号楼一层', 999),
    (2, '演示机房B', 'DEMO_ROOM_B', '二号楼一层', 999);

-- ==================== 3. 点位定义 ====================
INSERT INTO point_definition (id, point_code, point_name, unit, data_type, description, organization_id) VALUES
    (1, 'temperature', '温度',     '℃', 'NUMBER', '环境温度', 999),
    (2, 'humidity',    '湿度',     '%',  'NUMBER', '环境湿度', 999),
    (3, 'voltage_a',   'A相电压',  'V',  'NUMBER', 'UPS输出A相电压', 999),
    (4, 'current_a',   'A相电流',  'A',  'NUMBER', 'UPS输出A相电流', 999);

-- ==================== 4. 类型点位模板 ====================
-- 设备类型 ID 依据 V3 种子自增顺序：1=UPS, 2=PDU_METER, 3=PRECISION_AC, 4=TEMP_HUMIDITY, 5=LEAK_DETECTOR
INSERT INTO device_type_point_template (id, type_id, point_id, organization_id, is_required, sort_order) VALUES
    (1, 4, 1, 999, 1, 1),
    (2, 4, 2, 999, 1, 2),
    (3, 1, 3, 999, 1, 1),
    (4, 1, 4, 999, 1, 2);

-- ==================== 5. 设备（每机房2台） ====================
-- 设备4 last_online_time 30分钟前：演示离线检测（OFFLINE 规则阈值15分钟）
INSERT INTO devices (id, device_name, device_code, type_id, organization_id, cloud_room_id, status, location, last_online_time) VALUES
    (1, '机房A-UPS-01',   'UPS-ROOM1-A', 1, 999, 1, 1, '演示机房A-01机柜', NOW()),
    (2, '机房A-温湿度-01', 'TH-ROOM1-B',  4, 999, 1, 1, '演示机房A-02机柜', NOW()),
    (3, '机房B-UPS-01',   'UPS-ROOM2-A', 1, 999, 2, 1, '演示机房B-01机柜', NOW()),
    (4, '机房B-温湿度-01', 'TH-ROOM2-B',  4, 999, 2, 0, '演示机房B-02机柜', DATE_SUB(NOW(), INTERVAL 30 MINUTE));

-- ==================== 6. 设备点位绑定 ====================
-- 设备2 的 voltage_a 绑定置为停用（status=1）：演示上报绑定校验拒绝
INSERT INTO device_point_binding (device_id, point_id, status) VALUES
    (1, 3, 0),
    (1, 4, 0),
    (2, 1, 0),
    (2, 2, 0),
    (2, 3, 1),
    (3, 3, 0),
    (3, 4, 0),
    (4, 1, 0),
    (4, 2, 0);

-- ==================== 7. 点位告警规则 ====================
-- 规则1-3：TEMP_HUMIDITY 温度阈值规则（挂在模板项1=type4+temperature）
-- 规则4：离线通用规则（rule_type=OFFLINE，point_id 为空，threshold=15分钟）
-- 规则5：设备级覆盖（设备2 高温阈值收紧为30，演示按点位替换式降级）
INSERT INTO device_point_alarm_rule
    (id, template_item_id, device_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, organization_id, remark) VALUES
    (1, 1, NULL, 1,    'METRIC', '高温警告',     'GT', 35.0000, 2, 1, 999, '全局：温度高于35℃触发警告'),
    (2, 1, NULL, 1,    'METRIC', '高温紧急',     'GT', 40.0000, 1, 1, 999, '全局：温度高于40℃触发紧急'),
    (3, 1, NULL, 1,    'METRIC', '低温警告',     'LT', 10.0000, 4, 1, 999, '全局：温度低于10℃触发警告'),
    (4, 1, NULL, NULL, 'OFFLINE', '设备离线',    NULL, 15.0000, 2, 1, 999, '全局：超过15分钟未上报判定离线'),
    (5, 1, 2,    1,    'METRIC', '高温警告(设备级)', 'GT', 30.0000, 2, 1, 999, '设备级覆盖：机房A-温湿度-01 高温阈值收紧为30');

-- ==================== 8. 保留策略（全局默认） ====================
INSERT INTO metric_retention_policy (id, point_id, retention_days) VALUES
    (1, NULL, 90);
