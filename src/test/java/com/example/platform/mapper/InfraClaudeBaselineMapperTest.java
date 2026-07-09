package com.example.platform.mapper;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.Organization;
import com.example.platform.entity.PointDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INFRA-CLAUDE-01 基础模型验证测试。
 *
 * 目的：针对新增的 Flyway 基线（V1-V5）与13个新增实体/Mapper，
 * 在真实 MySQL 数据库上做最小可行的 CRUD 冒烟测试，
 * 确认表结构、外键约束、MyBatis-Plus 自动填充（create_time/update_time）、
 * 逻辑删除（deleted）等基础设施按契约正确工作。
 *
 * 覆盖对象：Organization -> CloudRoom -> DataScopeAssignment（组织/机房/数据范围链路），
 * PointDefinition -> AlarmEvent（点位与告警事件链路）。
 * 具体业务逻辑（如数据范围校验规则、告警状态机流转规则）由 INFRA-CLAUDE-02 负责，
 * 本测试仅验证持久化层本身可用。
 *
 * @Transactional 保证每个测试方法的数据变更在结束后自动回滚，不污染真实数据库。
 */
@SpringBootTest
@Transactional
class InfraClaudeBaselineMapperTest {

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private CloudRoomMapper cloudRoomMapper;

    @Autowired
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;

    @Autowired
    private PointDefinitionMapper pointDefinitionMapper;

    @Autowired
    private AlarmEventMapper alarmEventMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Test
    void testOrganizationInsertAndAutoFillTimestamps() {
        Organization org = new Organization();
        org.setOrgName("测试组织-INFRA01");
        org.setOrgCode("ORG-INFRA01-" + System.nanoTime());
        org.setDescription("INFRA-CLAUDE-01 基础模型验证");
        org.setStatus(0);

        organizationMapper.insert(org);

        assertNotNull(org.getId(), "插入后组织ID应自动填充");
        assertNotNull(org.getCreateTime(), "create_time 应由 MyBatis-Plus 自动填充");
        assertNotNull(org.getUpdateTime(), "update_time 应由 MyBatis-Plus 自动填充");

        Organization found = organizationMapper.selectById(org.getId());
        assertNotNull(found, "应能查到刚插入的组织");
        assertEquals(org.getOrgCode(), found.getOrgCode());
        assertEquals(0, found.getDeleted(), "新插入记录逻辑删除字段应为0（未删除）");
    }

    @Test
    void testOrganizationLogicalDelete() {
        Organization org = new Organization();
        org.setOrgName("待删除测试组织");
        org.setOrgCode("ORG-DEL-" + System.nanoTime());
        org.setStatus(0);
        organizationMapper.insert(org);

        int rows = organizationMapper.deleteById(org.getId());
        assertEquals(1, rows, "逻辑删除应影响1行");

        Organization afterDelete = organizationMapper.selectById(org.getId());
        assertNull(afterDelete, "逻辑删除后 MyBatis-Plus 默认查询不应再返回该记录");
    }

    @Test
    void testCloudRoomForeignKeyToOrganization() {
        Organization org = new Organization();
        org.setOrgName("机房归属测试组织");
        org.setOrgCode("ORG-ROOM-" + System.nanoTime());
        org.setStatus(0);
        organizationMapper.insert(org);

        CloudRoom room = new CloudRoom();
        room.setOrganizationId(org.getId());
        room.setRoomName("测试机房A");
        room.setRoomCode("ROOM-A-" + System.nanoTime());
        room.setLocation("测试园区");
        room.setStatus(0);
        cloudRoomMapper.insert(room);

        assertNotNull(room.getId(), "插入后机房ID应自动填充");

        CloudRoom found = cloudRoomMapper.selectById(room.getId());
        assertNotNull(found);
        assertEquals(org.getId(), found.getOrganizationId(), "机房应正确关联到所属组织");
    }

    @Test
    void testCloudRoomForeignKeyViolationRejected() {
        CloudRoom room = new CloudRoom();
        room.setOrganizationId(-999999L); // 不存在的组织ID
        room.setRoomName("非法机房");
        room.setRoomCode("ROOM-INVALID-" + System.nanoTime());
        room.setStatus(0);

        assertThrows(Exception.class, () -> cloudRoomMapper.insert(room),
                "引用不存在的organization_id应触发外键约束异常，证明V2迁移的外键约束生效");
    }

    @Test
    void testDataScopeAssignmentInsert() {
        Organization org = new Organization();
        org.setOrgName("数据范围测试组织");
        org.setOrgCode("ORG-SCOPE-" + System.nanoTime());
        org.setStatus(0);
        organizationMapper.insert(org);

        // sys_data_scope_assignment.user_id 存在外键约束，空库场景下不能假设已有用户，
        // 这里显式插入一个测试用户以满足约束（真实密码由AuthService负责，测试仅需满足非空+外键）。
        com.example.platform.entity.SysUser user = new com.example.platform.entity.SysUser();
        user.setUsername("infra01_test_" + (System.nanoTime() % 100000));
        user.setPassword("Test@1234");
        user.setNickname("INFRA01测试用户");
        user.setRole("VIEWER");
        user.setStatus(0);
        sysUserMapper.insert(user);

        DataScopeAssignment scope = new DataScopeAssignment();
        scope.setUserId(user.getId());
        scope.setOrganizationId(org.getId());
        scope.setScopeType("ORG_ALL");
        dataScopeAssignmentMapper.insert(scope);

        assertNotNull(scope.getId());
        DataScopeAssignment found = dataScopeAssignmentMapper.selectById(scope.getId());
        assertNotNull(found);
        assertEquals("ORG_ALL", found.getScopeType());
    }

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
