-- ============================================================
-- V15: 告警模版一级实体化（阶段五·点位模版重构）
--   alarm_template / alarm_template_item 两表直连，替代
--   device_point_alarm_rule 的"模板项锚定+设备覆盖"散行模型；
--   device_types / devices 各增 alarm_template_id 绑定列
--   （设备改绑优先，空=跟随类型基础模版）。
--   旧表 device_point_alarm_rule 数据冻结保留（迁移源），
--   告警评估/离线检测链路切换读新表，V16 删除旧表。
-- ============================================================

-- ==================== 1. 告警模版主表 ====================
CREATE TABLE alarm_template (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模版ID（主键）',
    template_code VARCHAR(50)  NOT NULL COMMENT '模版编码，如 TEMP_HUM_STD',
    template_name VARCHAR(100) NOT NULL COMMENT '显示名，如"温湿度标准监控"',
    description   VARCHAR(300) DEFAULT NULL COMMENT '用途说明，如"机房通用温湿度阈值"',
    status        TINYINT NOT NULL DEFAULT 0 COMMENT '0-启用 1-停用',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_alarm_template_code UNIQUE (template_code)
) COMMENT '告警规则融合模版';

-- ==================== 2. 模版规则行 ====================
CREATE TABLE alarm_template_item (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则行ID（主键）',
    template_id  BIGINT NOT NULL COMMENT '所属模版（关联 alarm_template）',
    point_id     BIGINT DEFAULT NULL COMMENT '关联点位（通用规则如 OFFLINE 为空）',
    rule_type    VARCHAR(20) NOT NULL DEFAULT 'METRIC' COMMENT 'METRIC-阈值 / OFFLINE-离线 / COMM-通信异常',
    rule_name    VARCHAR(100) NOT NULL COMMENT '规则名（告警文案前缀）',
    operator     VARCHAR(10) DEFAULT NULL COMMENT '比较符：GT/LT/GE/LE/EQ/NE；OFFLINE/COMM 为空',
    threshold    DECIMAL(18,4) DEFAULT NULL COMMENT '阈值；OFFLINE 规则=离线分钟数（1-1440）',
    alarm_level  TINYINT NOT NULL DEFAULT 3 COMMENT '告警级别：1紧急-5提示（数字小=严重）',
    enabled      TINYINT NOT NULL DEFAULT 1 COMMENT '0-停用 1-启用',
    sort_order   INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    KEY idx_tpl_item_tpl (template_id),
    KEY idx_tpl_item_point (point_id)
) COMMENT '告警模版规则行';

-- ==================== 3. 绑定列 ====================
ALTER TABLE device_types ADD COLUMN alarm_template_id BIGINT DEFAULT NULL COMMENT '该类型的基础告警模版' AFTER description;
ALTER TABLE devices      ADD COLUMN alarm_template_id BIGINT DEFAULT NULL COMMENT '设备改绑的模版（空=跟随类型）' AFTER type_id;

-- ==================== 4. 类型基础模版迁移 ====================
-- 每个存在模板级规则（device_id IS NULL）的设备类型生成一个基础模版
INSERT INTO alarm_template (template_code, template_name, description, status)
SELECT CONCAT('BASE_', t.type_code),
       CONCAT(t.type_name, '-基础告警模版'),
       CONCAT('由类型 ', t.type_code, ' 模板级规则迁移生成（V15）'),
       0
FROM device_types t
WHERE t.deleted = 0
  AND EXISTS (SELECT 1
              FROM device_point_alarm_rule r
              JOIN device_type_point_template it ON it.id = r.template_item_id
              WHERE it.type_id = t.id AND r.device_id IS NULL AND r.deleted = 0);

-- 模板级规则迁入基础模版规则行
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT at2.id, r.point_id, r.rule_type, r.rule_name, r.operator, r.threshold, r.alarm_level, r.enabled, 0
FROM device_point_alarm_rule r
JOIN device_type_point_template it ON it.id = r.template_item_id
JOIN device_types t ON t.id = it.type_id
JOIN alarm_template at2 ON at2.template_code = CONCAT('BASE_', t.type_code)
WHERE r.device_id IS NULL AND r.deleted = 0;

-- 类型回填基础模版绑定
UPDATE device_types t
JOIN alarm_template at2 ON at2.template_code = CONCAT('BASE_', t.type_code)
SET t.alarm_template_id = at2.id
WHERE t.deleted = 0;

-- ==================== 5. 设备覆盖规则 → 专用模版 ====================
-- 有设备级覆盖规则的设备：复制类型基础模版 → 按 pointId 整组替换为覆盖规则 → 改绑设备
CREATE PROCEDURE migrate_device_override_templates()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_device_id INT;
    DECLARE v_device_name VARCHAR(50);
    DECLARE v_device_code VARCHAR(100);
    DECLARE v_type_id INT;
    DECLARE v_new_tpl_id BIGINT;
    DECLARE cur CURSOR FOR
        SELECT DISTINCT d.id, d.device_name, d.device_code, d.type_id
        FROM devices d
        JOIN device_point_alarm_rule o ON o.device_id = d.id AND o.deleted = 0
        WHERE d.deleted = 0;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    tpl_loop: LOOP
        FETCH cur INTO v_device_id, v_device_name, v_device_code, v_type_id;
        IF done = 1 THEN
            LEAVE tpl_loop;
        END IF;

        INSERT INTO alarm_template (template_code, template_name, description, status)
        VALUES (CONCAT('DEV_', v_device_id, '_', LEFT(v_device_code, 40)),
                CONCAT(v_device_name, '-专用模版'),
                '由设备级覆盖规则迁移生成（V15）',
                0);
        SET v_new_tpl_id = LAST_INSERT_ID();

        -- 基础模版中未被覆盖点位组取代的规则行
        INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
        SELECT v_new_tpl_id, r.point_id, r.rule_type, r.rule_name, r.operator, r.threshold, r.alarm_level, r.enabled, 0
        FROM device_point_alarm_rule r
        JOIN device_type_point_template it ON it.id = r.template_item_id
        WHERE it.type_id = v_type_id
          AND r.device_id IS NULL AND r.deleted = 0
          AND NOT EXISTS (SELECT 1
                          FROM device_point_alarm_rule o
                          WHERE o.device_id = v_device_id AND o.deleted = 0
                            AND ((o.point_id IS NULL AND r.point_id IS NULL) OR o.point_id = r.point_id));

        -- 覆盖规则行整组生效
        INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
        SELECT v_new_tpl_id, o.point_id, o.rule_type, o.rule_name, o.operator, o.threshold, o.alarm_level, o.enabled, 0
        FROM device_point_alarm_rule o
        WHERE o.device_id = v_device_id AND o.deleted = 0;

        UPDATE devices SET alarm_template_id = v_new_tpl_id WHERE id = v_device_id;
    END LOOP;
    CLOSE cur;
END;

CALL migrate_device_override_templates();
DROP PROCEDURE migrate_device_override_templates;
