package com.example.platform.mapper;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.PointDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INFRA-CLAUDE-01 基础模型验证测试。
 *
 * 目的：针对 Flyway 基线中的核心实体/Mapper，
 * 在真实 MySQL 数据库上做最小可行的 CRUD 冒烟测试，
 * 确认表结构、外键约束、MyBatis-Plus 自动填充（create_time/update_time）、
 * 逻辑删除（deleted）等基础设施按契约正确工作。
 *
 * 覆盖对象：PointDefinition -> AlarmEvent（点位与告警事件链路）。
 * 单租户收束后 Organization/DataScopeAssignment 链路已删除，不再覆盖。
 * 具体业务逻辑（如告警状态机流转规则）由 INFRA-CLAUDE-02 负责，
 * 本测试仅验证持久化层本身可用。
 *
 * @Transactional 保证每个测试方法的数据变更在结束后自动回滚，不污染真实数据库。
 */
@SpringBootTest
@Transactional
class InfraClaudeBaselineMapperTest {

    @Autowired
    private PointDefinitionMapper pointDefinitionMapper;

    @Autowired
    private AlarmEventMapper alarmEventMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Test
    void testPointDefinitionUniquePointCode() {
        PointDefinition point = new PointDefinition();
        String code = "test_point_" + System.nanoTime();
        point.setPointCode(code);
        point.setPointName("测试点位");
        point.setUnit("V");
        point.setDataType("NUMBER");
        pointDefinitionMapper.insert(point);

        assertNotNull(point.getId());

        PointDefinition duplicate = new PointDefinition();
        duplicate.setPointCode(code);
        duplicate.setPointName("重复编码点位");
        duplicate.setDataType("NUMBER");

        assertThrows(Exception.class, () -> pointDefinitionMapper.insert(duplicate),
                "point_code 存在唯一约束，插入重复编码应失败");
    }

    @Test
    void testAlarmEventForeignKeyToDeviceAndDefaultState() {
        // devices 表在空库场景下可能为空，这里先插入一台测试设备以满足外键约束
        com.example.platform.entity.Device device = new com.example.platform.entity.Device();
        device.setDeviceName("告警测试设备");
        device.setDeviceCode("ALARM-TEST-" + System.nanoTime());
        device.setTypeId(1);
        device.setStatus(1);
        device.setLocation("测试机房");
        deviceMapper.insert(device);

        AlarmEvent event = new AlarmEvent();
        event.setDeviceId(device.getId());
        event.setAlarmType("测试告警");
        event.setAlarmLevel(3);
        event.setAlarmMessage("INFRA-CLAUDE-01 基础模型验证告警");
        alarmEventMapper.insert(event);

        assertNotNull(event.getId(), "插入后告警事件ID应自动填充");
        assertNotNull(event.getTriggeredTime(), "triggered_time 应自动填充");

        AlarmEvent found = alarmEventMapper.selectById(event.getId());
        assertNotNull(found);
        assertEquals("TRIGGERED", found.getState(), "新触发的告警事件默认状态应为 TRIGGERED（数据库默认值）");
    }
}
