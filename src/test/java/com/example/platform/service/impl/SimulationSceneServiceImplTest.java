package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationSceneMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.PointDefinitionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:SimulationSceneServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-01：场景机房存在性校验、场景编码全局唯一、五类设备类型校验、步骤值校验
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationSceneService 单元测试")
class SimulationSceneServiceImplTest {

    @Mock
    private SimulationSceneMapper simulationSceneMapper;
    @Mock
    private SimulationSceneStepMapper simulationSceneStepMapper;
    @Mock
    private CloudRoomService cloudRoomService;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private SimulationSceneServiceImpl simulationSceneService;

    private static final Long CURRENT_USER_ID = 30L;
    private static final Long CLOUD_ROOM_ID = 21L;

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

    private CloudRoom buildCloudRoom(Long id) {
        CloudRoom cloudRoom = new CloudRoom();
        cloudRoom.setId(id);
        cloudRoom.setRoomCode("ROOM-" + id);
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

    private SimulationScene buildScene(Long id) {
        SimulationScene scene = new SimulationScene();
        scene.setId(id);
        scene.setSceneCode("SCENE_" + id);
        scene.setSceneName("场景" + id);
        scene.setStatus(0);
        return scene;
    }

    @Nested
    @DisplayName("createScene - 创建场景")
    class CreateScene {

        @Test
        @DisplayName("机房不存在时拒绝创建")
        void should_reject_when_cloudRoomNotFound() {
            when(cloudRoomService.getCloudRoomOrThrow(CLOUD_ROOM_ID, null))
                    .thenThrow(new BusinessException("机房不存在"));

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("SCENE_X");
            dto.setSceneName("场景X");
            dto.setCloudRoomId(CLOUD_ROOM_ID);

            assertThatThrownBy(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
            verify(simulationSceneMapper, times(0)).insert((SimulationScene) any());
        }

        @Test
        @DisplayName("非五类设备类型编码拒绝创建")
        void should_reject_when_deviceTypeUnsupported() {
            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("SCENE_X");
            dto.setSceneName("场景X");
            dto.setDeviceTypeCode("UNKNOWN_TYPE");

            assertThatThrownBy(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("五类支持范围");
            verify(simulationSceneMapper, times(0)).insert((SimulationScene) any());
        }

        @Test
        @DisplayName("成功创建场景并写入步骤")
        void should_createSceneWithSteps() {
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
                    .thenReturn(buildScene(1L));
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
            dto.setDeviceTypeCode("UPS");
            dto.setSteps(List.of(stepDto));

            SimulationSceneVO vo = simulationSceneService.createScene(dto, CURRENT_USER_ID);
            assertThat(vo.getSceneCode()).isEqualTo("SCENE_1");
            verify(simulationSceneMapper, times(1)).insert((SimulationScene) any());
            verify(simulationSceneStepMapper, times(1)).insert((SimulationSceneStep) any());
        }

        @Test
        @DisplayName("绑定机房创建场景成功")
        void should_createSceneWithCloudRoom() {
            when(cloudRoomService.getCloudRoomOrThrow(CLOUD_ROOM_ID, null))
                    .thenReturn(buildCloudRoom(CLOUD_ROOM_ID));
            when(simulationSceneMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(simulationSceneMapper.insert((SimulationScene) any())).thenAnswer(inv -> {
                SimulationScene scene = inv.getArgument(0);
                scene.setId(2L);
                return 1;
            });
            SimulationScene persisted = buildScene(2L);
            persisted.setCloudRoomId(CLOUD_ROOM_ID);
            when(simulationSceneMapper.selectById(2L)).thenReturn(persisted);
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            SimulationSceneCreateDTO dto = new SimulationSceneCreateDTO();
            dto.setSceneCode("PDU_TEST");
            dto.setSceneName("PDU 电表测试");
            dto.setCloudRoomId(CLOUD_ROOM_ID);
            dto.setDeviceTypeCode("PDU_METER");

            assertThatCode(() -> simulationSceneService.createScene(dto, CURRENT_USER_ID))
                    .doesNotThrowAnyException();
            verify(cloudRoomService).getCloudRoomOrThrow(CLOUD_ROOM_ID, null);
            verify(simulationSceneMapper).insert((SimulationScene) any());
        }
    }

    @Nested
    @DisplayName("getSceneOrThrow - 查询场景")
    class GetSceneOrThrow {

        @Test
        @DisplayName("场景不存在抛 SIMULATION_SCENE_NOT_FOUND")
        void should_throw_when_notFound() {
            when(simulationSceneMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> simulationSceneService.getSceneOrThrow(99L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("任意已认证用户可访问存在的场景")
        void should_return_when_exists() {
            when(simulationSceneMapper.selectById(5L)).thenReturn(buildScene(5L));

            SimulationScene result = simulationSceneService.getSceneOrThrow(5L, CURRENT_USER_ID);
            assertThat(result.getId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("pageScenes - 分页查询")
    class PageScenes {

        @Test
        @DisplayName("分页返回场景列表")
        void should_returnPagedScenes() {
            Page<SimulationScene> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildScene(1L)));
            mockPage.setTotal(1);
            when(simulationSceneMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<SimulationSceneVO> result = simulationSceneService.pageScenes(
                    CURRENT_USER_ID, 1, 10, null, null, null);
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
                    .thenReturn(buildScene(1L));
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
                    .thenReturn(buildScene(1L));
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
                    .thenReturn(buildScene(1L));
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
