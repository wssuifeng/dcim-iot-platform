-- ============================================================
-- Flyway seed V9: 开发环境基础测试数据（仅 dev profile 加载）
--
-- 加载方式：application-dev.yaml 中 spring.flyway.locations 追加 classpath:db/seed；
--   prod 不包含该 location，生产库不会注入任何演示数据。
-- 版本号取 V9（排在 db/migration V1-V8 之后执行）；文档原写 V1 会与 migration/V1
--   产生 Flyway 多 location 版本冲突，此处为修正。
--
-- 内容：跑通"上报→评估→告警→通知"全链的最小数据集（见设计文档 §9.3）。
--   固定主键写入，便于外键引用与测试断言；密码统一 Admin123。
--   metric_sample / alarm_event 不注入，由运行时上报与仿真产生。
-- ============================================================

-- ==================== 1. 用户（ID=1 平台管理员为测试前置依赖） ====================
INSERT INTO sys_user (id, username, password, nickname, role, platform_role, email, email_verified, status) VALUES
    (1, 'admin',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '平台管理员', 'VIEWER',      'PLATFORM_ADMIN', 'admin@demo.local',     1, 0),
    (2, 'orgadmin',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '演示组织管理员', 'ORG_ADMIN',   NULL, 'orgadmin@demo.local',  1, 0),
    (3, 'operator',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '演示运维员',   'OPERATOR',    NULL, 'operator@demo.local',  1, 0),
    (4, 'viewer',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '演示只读用户', 'VIEWER',      NULL, 'viewer@demo.local',    1, 0);

-- ==================== 2. 组织与机房 ====================
INSERT INTO sys_organization (id, org_name, org_code, description) VALUES
    (1, '演示组织', 'DEMO_ORG', '开发测试用演示组织');

INSERT INTO sys_cloud_room (id, organization_id, room_name, room_code, location) VALUES
    (1, 1, '演示机房A', 'DEMO_ROOM_A', '一号楼一层'),
    (2, 1, '演示机房B', 'DEMO_ROOM_B', '二号楼一层');

-- ==================== 3. 组织成员关系 ====================
INSERT INTO organization_membership (user_id, organization_id, membership_role, status, approved_by, approved_time) VALUES
    (2, 1, 'ORG_ADMIN', 'ACTIVE', 1, NOW()),
    (3, 1, 'OPERATOR',  'ACTIVE', 1, NOW()),
    (4, 1, 'VIEWER',    'ACTIVE', 1, NOW());

-- ==================== 4. 数据范围（四级各一，验证范围行为） ====================
INSERT INTO sys_data_scope_assignment (user_id, organization_id, cloud_room_id, scope_type) VALUES
    (2, 1,    NULL, 'ORG_ALL'),
    (3, 1,    1,    'ROOMS'),
    (4, 1,    NULL, 'READ_ONLY');

-- ==================== 5. 平台级点位定义（organization_id=0） ====================
INSERT INTO point_definition (id, point_code, point_name, unit, data_type, description, organization_id) VALUES
    (1, 'temperature', '温度',     '℃', 'NUMBER', '环境温度',       0),
    (2, 'humidity',    '湿度',     '%',  'NUMBER', '环境湿度',       0),
    (3, 'voltage_a',   'A相电压',  'V',  'NUMBER', 'UPS输出A相电压', 0),
    (4, 'current_a',   'A相电流',  'A',  'NUMBER', 'UPS输出A相电流', 0);

-- ==================== 6. 平台级类型点位模板 ====================
-- 设备类型 ID 依据 V3 种子自增顺序：1=UPS, 2=PDU_METER, 3=PRECISION_AC, 4=TEMP_HUMIDITY, 5=LEAK_DETECTOR
INSERT INTO device_type_point_template (id, type_id, point_id, organization_id, is_required, sort_order) VALUES
    (1, 4, 1, 0, 1, 1),
    (2, 4, 2, 0, 1, 2),
    (3, 1, 3, 0, 1, 1),
    (4, 1, 4, 0, 1, 2);

-- ==================== 7. 设备（org=1，每机房2台） ====================
-- 设备4 last_online_time 30分钟前：演示离线检测（OFFLINE 规则阈值15分钟）
INSERT INTO devices (id, device_name, device_code, type_id, organization_id, cloud_room_id, status, location, last_online_time) VALUES
    (1, '机房A-UPS-01',   'UPS-ROOM1-A', 1, 1, 1, 1, '演示机房A-01机柜', NOW()),
    (2, '机房A-温湿度-01', 'TH-ROOM1-B',  4, 1, 1, 1, '演示机房A-02机柜', NOW()),
    (3, '机房B-UPS-01',   'UPS-ROOM2-A', 1, 1, 2, 1, '演示机房B-01机柜', NOW()),
    (4, '机房B-温湿度-01', 'TH-ROOM2-B',  4, 1, 2, 0, '演示机房B-02机柜', DATE_SUB(NOW(), INTERVAL 30 MINUTE));

-- ==================== 8. 设备点位绑定 ====================
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

-- ==================== 9. 点位告警规则 ====================
-- 规则1-3：平台级 TEMP_HUMIDITY 温度阈值规则（挂在模板项1=type4+temperature）
-- 规则4：平台级离线通用规则（rule_type=OFFLINE，point_id 为空，threshold=15分钟）
-- 规则5：设备级覆盖（设备2 高温阈值收紧为30，演示按点位替换式降级）
INSERT INTO device_point_alarm_rule
    (id, template_item_id, device_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, organization_id, remark) VALUES
    (1, 1, NULL, 1,    'METRIC', '高温警告',     'GT', 35.0000, 2, 1, 0, '平台级：温度高于35℃触发警告'),
    (2, 1, NULL, 1,    'METRIC', '高温紧急',     'GT', 40.0000, 1, 1, 0, '平台级：温度高于40℃触发紧急'),
    (3, 1, NULL, 1,    'METRIC', '低温警告',     'LT', 10.0000, 4, 1, 0, '平台级：温度低于10℃触发警告'),
    (4, 1, NULL, NULL, 'OFFLINE', '设备离线',    NULL, 15.0000, 2, 1, 0, '平台级：超过15分钟未上报判定离线'),
    (5, 1, 2,    1,    'METRIC', '高温警告(设备级)', 'GT', 30.0000, 2, 1, 1, '设备级覆盖：机房A-温湿度-01 高温阈值收紧为30');

-- ==================== 10. 保留策略（全局默认） ====================
INSERT INTO metric_retention_policy (id, point_id, retention_days) VALUES
    (1, NULL, 90);
