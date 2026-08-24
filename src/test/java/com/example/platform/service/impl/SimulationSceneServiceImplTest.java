package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneUpdateDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationSceneMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.PointDefinitionService;
import com.example.platform.service.SimulationSceneService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:SimulationSceneServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-01：场景范围校验、组织/机房越权拒绝、场景编码同组织唯一、五类设备类型校验
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationSceneService 单元测试")
class SimulationSceneServiceImplTest {

    @Mock
    private SimulationSceneMapper simulationSceneMapper;
    @Mock
    private SimulationSceneStepMapper simulationSceneStepMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private CloudRoomService cloudRoomService;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private SimulationSceneServiceImpl simulationSceneService;

    private static final Long CURRENT_USER_ID = 30L;
    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 999L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = SimulationSceneServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(simulationSceneService, simulationSceneMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(SimulationSceneMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SimulationScene.class);
        TableInfoHelper.initTableInfo(assistant, SimulationSceneStep.class);
    }

    private DataScopeContext orgAdminContext(Long orgId) {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("ORG_ADMIN")
                .organizationIds(Set.of(orgId))
                .scopeType("ORG_ALL")
                .build();
    }

    private CloudRoom buildCloudRoom(Long id, Long organizationId) {
        CloudRoom cloudRoom = new CloudRoom();
        cloudRoom.setId(id);
        cloudRoom.setOrganizationId(organizationId);
        return cloudRoom;
    }

    private PointDefinition buildPoint(Long id, String dataType) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode("voltage_a");
        p.setPointName("A相电压");
        p.setUnit("V");
        p.setDataType(dataType);
        return p;
    }

    private SimulationScene buildScene(Long id, Long orgId) {
        SimulationScene scene = new SimulationScene();
        scene.setId(id);
        scene.setSceneCode("SCENE_" + id);
        scene.setSceneName("场景" + id);
        scene.setOrganizationId(orgId);
        scene.setStatus(0);
        return scene;
    }

    @Nested
    @DisplayName("createScene - 创建场景")
    class CreateScene {

        @Test
        @DisplayName("越权组织拒绝创建")
        void should_reject_when_orgAccessDenied() {
            DataScopeContext context = DataScopeContext.builder()
                    .userId(CURRENT_USER_ID).role("ORG_ADMIN")
                    .organizationIds(Set.of(ORG_ID)).scopeType("ORG_ALL").build();
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("SCENE_X");
            dto.setSceneName("场景X");
            dto.setOrganizationId(OTHER_ORG_ID);

            assertThatThrownBy(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(simulationSceneMapper, times(0)).insert((SimulationScene) any());
        }

        @Test
        @DisplayName("非五类设备类型编码拒绝创建")
        void should_reject_when_deviceTypeUnsupported() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("SCENE_X");
            dto.setSceneName("场景X");
            dto.setOrganizationId(ORG_ID);
            dto.setDeviceTypeCode("UNKNOWN_TYPE");

            assertThatThrownBy(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("五类支持范围");
            verify(simulationSceneMapper, times(0)).insert((SimulationScene) any());
        }

        @Test
        @DisplayName("成功创建场景并写入步骤")
        void should_createSceneWithSteps() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(simulationSceneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L, CURRENT_USER_ID))
                    .thenReturn(buildPoint(10L, "NUMBER"));
            when(simulationSceneMapper.insert((SimulationScene) any())).thenAnswer(inv -> {
                SimulationScene s = inv.getArgument(0);
                s.setId(1L);
                return 1;
            });
            when(simulationSceneStepMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(simulationSceneStepMapper.insert((SimulationSceneStep) any())).thenReturn(1);
            when(simulationSceneMapper.selectById(1L))
                    .thenReturn(buildScene(1L, ORG_ID));
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            SimulationSceneStepDTO stepDto = new SimulationSceneStepDTO();
            stepDto.setStepOrder(1);
            stepDto.setPointId(10L);
            stepDto.setSampleValue(new BigDecimal("220.5"));
            stepDto.setAction("WRITE_SAMPLE");

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("UPS_LOAD");
            dto.setSceneName("UPS 负载测试");
            dto.setOrganizationId(ORG_ID);
            dto.setDeviceTypeCode("UPS");
            dto.setSteps(List.of(stepDto));

            SimulationSceneVO vo = simulationSceneService.createScene(dto, CURRENT_USER_ID);
            assertThat(vo.getOrganizationId()).isEqualTo(ORG_ID);
            verify(simulationSceneMapper, times(1)).insert((SimulationScene) any());
            verify(simulationSceneStepMapper, times(1)).insert((SimulationSceneStep) any());
        }

        @Test
        @DisplayName("ORG_ADMIN 无需显式机房授权即可在本组织机房创建场景")
        void should_allowOrgAdminToCreateSceneInOrganizationRoom() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(cloudRoomService.getCloudRoomOrThrow(21L, CURRENT_USER_ID))
                    .thenReturn(buildCloudRoom(21L, ORG_ID));
            when(simulationSceneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(simulationSceneMapper.insert((SimulationScene) any())).thenAnswer(inv -> {
                SimulationScene scene = inv.getArgument(0);
                scene.setId(2L);
                return 1;
            });
            SimulationScene persisted = buildScene(2L, ORG_ID);
            persisted.setCloudRoomId(21L);
            when(simulationSceneMapper.selectById(2L)).thenReturn(persisted);
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("PDU_TEST");
            dto.setSceneName("PDU 电表测试");
            dto.setOrganizationId(ORG_ID);
            dto.setCloudRoomId(21L);
            dto.setDeviceTypeCode("PDU_METER");

            assertThatCode(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .doesNotThrowAnyException();
            verify(cloudRoomService).getCloudRoomOrThrow(21L, CURRENT_USER_ID);
            verify(simulationSceneMapper).insert((SimulationScene) any());
        }

        @Test
        @DisplayName("机房不属于场景组织时拒绝创建")
        void should_rejectWhenCloudRoomBelongsToAnotherOrganization() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(cloudRoomService.getCloudRoomOrThrow(21L, CURRENT_USER_ID))
                    .thenReturn(buildCloudRoom(21L, OTHER_ORG_ID));

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("PDU_TEST");
            dto.setSceneName("PDU 电表测试");
            dto.setOrganizationId(ORG_ID);
            dto.setCloudRoomId(21L);

            assertThatThrownBy(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class)
                    .hasMessageContaining("CloudRoom");
            verify(simulationSceneMapper, times(0)).insert((SimulationScene) any());
        }
    }

    @Nested
    @DisplayName("getSceneOrThrow - 范围校验")
    class GetSceneOrThrow {

        @Test
        @DisplayName("越权组织拒绝访问场景")
        void should_reject_when_orgNotInScope() {
            when(simulationSceneMapper.selectById(5L))
                    .thenReturn(buildScene(5L, OTHER_ORG_ID));
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> simulationSceneService.getSceneOrThrow(5L, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("场景不存在抛 SIMULATION_SCENE_NOT_FOUND")
        void should_throw_when_notFound() {
            when(simulationSceneMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> simulationSceneService.getSceneOrThrow(99L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("pageScenes - 分页范围过滤")
    class PageScenes {

        @Test
        @DisplayName("无组织授权返回空结果")
        void should_returnEmpty_when_noOrgAssigned() {
            DataScopeContext context = DataScopeContext.builder()
                    .userId(CURRENT_USER_ID).role("VIEWER")
                    .organizationIds(Set.of()).scopeType("READ_ONLY").build();
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);

            Page<SimulationSceneVO> result = simulationSceneService.pageScenes(
                    CURRENT_USER_ID, 1, 10, null, null, null, null);
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
            verify(simulationSceneMapper, times(0))
                    .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("非 PLATFORM_ADMIN 仅查授权组织")
        void should_filterByOrgIds() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            Page<SimulationScene> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildScene(1L, ORG_ID)));
            mockPage.setTotal(1);
            when(simulationSceneMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<SimulationSceneVO> result = simulationSceneService.pageScenes(
                    CURRENT_USER_ID, 1, 10, null, null, null, null);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("addStep - 步骤写入校验")
    class AddStep {

        @Test
        @DisplayName("STRING 点位拒绝写入数值")
        void should_reject_when_stringPoint() {
            when(simulationSceneMapper.selectById(1L))
                    .thenReturn(buildScene(1L, ORG_ID));
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(pointDefinitionService.getPointDefinitionOrThrow(10L, CURRENT_USER_ID))
                    .thenReturn(buildPoint(10L, "STRING"));

            SimulationSceneStepDTO dto = new SimulationSceneStepDTO();
            dto.setStepOrder(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("1"));

            assertThatThrownBy(() -> simulationSceneService.addStep(1L, dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("STRING 点位不能写入");
            verify(simulationSceneStepMapper, times(0)).insert((SimulationSceneStep) any());
        }

        @Test
        @DisplayName("BOOLEAN 点位仅允许 0 或 1")
        void should_reject_when_booleanInvalid() {
            when(simulationSceneMapper.selectById(1L))
                    .thenReturn(buildScene(1L, ORG_ID));
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(pointDefinitionService.getPointDefinitionOrThrow(10L, CURRENT_USER_ID))
                    .thenReturn(buildPoint(10L, "BOOLEAN"));

            SimulationSceneStepDTO dto = new SimulationSceneStepDTO();
            dto.setStepOrder(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("5"));

            assertThatThrownBy(() -> simulationSceneService.addStep(1L, dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅允许 0 或 1");
        }

        @Test
        @DisplayName("非法动作拒绝")
        void should_reject_when_invalidAction() {
            when(simulationSceneMapper.selectById(1L))
                    .thenReturn(buildScene(1L, ORG_ID));
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(pointDefinitionService.getPointDefinitionOrThrow(10L, CURRENT_USER_ID))
                    .thenReturn(buildPoint(10L, "NUMBER"));

            SimulationSceneStepDTO dto = new SimulationSceneStepDTO();
            dto.setStepOrder(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));
            dto.setAction("DELETE");

            assertThatThrownBy(() -> simulationSceneService.addStep(1L, dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WRITE_SAMPLE 或 SLEEP");
        }
    }

    @Nested
    @DisplayName("setSceneStatus - 状态切换")
    class SetStatus {

        @Test
        @DisplayName("非法状态值拒绝")
        void should_reject_when_invalidStatus() {
            assertThatThrownBy(() -> simulationSceneService.setSceneStatus(
                    1L, 99, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }
}