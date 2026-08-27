package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.SimulationGeneratedDataRefVO;
import com.example.platform.dto.SimulationRunStartDTO;
import com.example.platform.dto.SimulationRunVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationGeneratedDataRef;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationGeneratedDataRefMapper;
import com.example.platform.mapper.SimulationRunRecordMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.PointDefinitionService;
import com.example.platform.service.SimulationRunExecutionService;
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
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:SimulationRunServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-01：生产门控阻断、生成数据可追溯、SLEEP 步骤跳过写入、取消运行
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationRunService 单元测试")
class SimulationRunServiceImplTest {

    @Mock
    private SimulationSceneService simulationSceneService;
    @Mock
    private SimulationSceneStepMapper simulationSceneStepMapper;
    @Mock
    private SimulationGeneratedDataRefMapper simulationGeneratedDataRefMapper;
    @Mock
    private SimulationRunRecordMapper simulationRunRecordMapper;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SimulationRunExecutionService runExecutionService;
    @Mock
    private CloudRoomService cloudRoomService;

    @InjectMocks
    private SimulationRunServiceImpl simulationRunService;

    private static final Long CURRENT_USER_ID = 30L;
    private static final Long CLOUD_ROOM_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
        // 默认关闭模拟写入开关，由具体测试用例覆盖
        ReflectionTestUtils.setField(simulationRunService, "writeEnabled", false);
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = SimulationRunServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(simulationRunService, simulationRunRecordMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(SimulationRunRecordMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SimulationRunRecord.class);
        TableInfoHelper.initTableInfo(assistant, SimulationGeneratedDataRef.class);
        TableInfoHelper.initTableInfo(assistant, SimulationSceneStep.class);
    }

    private SimulationScene buildEnabledScene(Long id) {
        SimulationScene scene = new SimulationScene();
        scene.setId(id);
        scene.setSceneCode("SCENE_" + id);
        scene.setSceneName("场景" + id);
        scene.setCloudRoomId(CLOUD_ROOM_ID);
        scene.setStatus(0);
        return scene;
    }

    private SimulationSceneStep buildWriteStep(Long id, Long sceneId, Integer deviceId, Long pointId) {
        SimulationSceneStep step = new SimulationSceneStep();
        step.setId(id);
        step.setSceneId(sceneId);
        step.setStepOrder(1);
        step.setPointId(pointId);
        step.setTargetDeviceId(deviceId);
        step.setSampleValue(new BigDecimal("220.5"));
        step.setAction("WRITE_SAMPLE");
        return step;
    }

    @Nested
    @DisplayName("startRun - 生产门控")
    class StartRunGate {

        @Test
        @DisplayName("写入开关关闭时拒绝启动")
        void should_reject_when_writeDisabled() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", false);

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            assertThatThrownBy(() -> simulationRunService.startRun(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.SIMULATION_WRITE_DISABLED);
            verify(simulationSceneService, never()).getSceneOrThrow(anyLong(), anyLong());
            verify(simulationRunRecordMapper, never()).insert((SimulationRunRecord) any());
        }
    }

    @Nested
    @DisplayName("startRun - 场景与执行")
    class StartRunExecute {

        @Test
        @DisplayName("场景已停用拒绝启动")
        void should_reject_when_sceneDisabled() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L);
            scene.setStatus(1);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID)).thenReturn(scene);

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            assertThatThrownBy(() -> simulationRunService.startRun(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已停用");
            verify(simulationRunRecordMapper, never()).insert((SimulationRunRecord) any());
        }

        @Test
        @DisplayName("空步骤场景拒绝启动")
        void should_reject_when_noSteps() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID))
                    .thenReturn(buildEnabledScene(1L));
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            assertThatThrownBy(() -> simulationRunService.startRun(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不包含任何步骤");
        }

        @Test
        @DisplayName("创建运行后立即返回 PENDING 并调度 WRITE_SAMPLE")
        void should_scheduleWriteRunAndReturnPending() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID)).thenReturn(scene);
            when(cloudRoomService.getCloudRoomOrThrow(CLOUD_ROOM_ID, CURRENT_USER_ID))
                    .thenReturn(new com.example.platform.entity.CloudRoom());
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(buildWriteStep(10L, 1L, 5, 100L)));

            Device device = new Device();
            device.setId(5);
            device.setTypeId(4);
            when(deviceScopeHelper.ensureDeviceAccess(5, CURRENT_USER_ID)).thenReturn(device);
            when(simulationRunRecordMapper.insert((SimulationRunRecord) any())).thenAnswer(inv -> {
                SimulationRunRecord run = inv.getArgument(0);
                run.setId(7L);
                return 1;
            });
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);
            dto.setTargetDeviceId(5);

            SimulationRunVO vo = simulationRunService.startRun(dto, CURRENT_USER_ID);

            assertThat(vo.getStatus()).isEqualTo("PENDING");
            assertThat(vo.getSuccessSteps()).isZero();
            verify(runExecutionService).executeAsync(7L, 5, CURRENT_USER_ID);
        }

        @Test
        @DisplayName("SLEEP 运行无需目标设备也可以进入后台队列")
        void should_scheduleSleepWithoutTargetDevice() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID)).thenReturn(scene);
            when(cloudRoomService.getCloudRoomOrThrow(CLOUD_ROOM_ID, CURRENT_USER_ID))
                    .thenReturn(new com.example.platform.entity.CloudRoom());

            SimulationSceneStep sleepStep = new SimulationSceneStep();
            sleepStep.setId(11L);
            sleepStep.setSceneId(1L);
            sleepStep.setStepOrder(1);
            sleepStep.setPointId(100L);
            sleepStep.setAction("SLEEP");
            sleepStep.setDelayMs(0);
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(sleepStep));
            when(simulationRunRecordMapper.insert((SimulationRunRecord) any())).thenAnswer(inv -> {
                SimulationRunRecord run = inv.getArgument(0);
                run.setId(8L);
                return 1;
            });
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            SimulationRunVO vo = simulationRunService.startRun(dto, CURRENT_USER_ID);

            assertThat(vo.getStatus()).isEqualTo("PENDING");
            verify(runExecutionService).executeAsync(8L, null, CURRENT_USER_ID);
        }
    }

    @Nested
    @DisplayName("cancelRun - 取消运行")
    class CancelRun {

        @Test
        @DisplayName("已结束运行不能取消")
        void should_reject_when_alreadyFinished() {
            SimulationRunRecord run = new SimulationRunRecord();
            run.setId(30L);
            run.setStatus("SUCCESS");
            when(simulationRunRecordMapper.selectById(30L)).thenReturn(run);

            assertThatThrownBy(() -> simulationRunService.cancelRun(30L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已结束");
        }

        @Test
        @DisplayName("成功取消运行中状态")
        void should_cancelRunning() {
            SimulationRunRecord run = new SimulationRunRecord();
            run.setId(31L);
            run.setStatus("RUNNING");
            when(simulationRunRecordMapper.selectById(31L)).thenReturn(run);
            when(simulationRunRecordMapper.update(any(), any())).thenReturn(1);

            SimulationRunVO vo = simulationRunService.cancelRun(31L, CURRENT_USER_ID);
            assertThat(vo.getStatus()).isEqualTo("CANCELLED");
            verify(simulationRunRecordMapper, times(1)).update(any(), any());
        }
    }

    @Nested
    @DisplayName("listRunEvents - 生成数据可追溯")
    class ListRunEvents {

        @Test
        @DisplayName("返回运行生成数据引用列表，填充点位编码")
        void should_returnEventsWithPointCode() {
            SimulationRunRecord run = new SimulationRunRecord();
            run.setId(40L);
            when(simulationRunRecordMapper.selectById(40L)).thenReturn(run);

            SimulationGeneratedDataRef ref = new SimulationGeneratedDataRef();
            ref.setId(1L);
            ref.setRunId(40L);
            ref.setDeviceId(5);
            ref.setPointId(100L);
            ref.setMetricSampleId(2001L);
            ref.setAction("WRITE_SAMPLE");
            ref.setSampleValue(new BigDecimal("220.5"));
            when(simulationGeneratedDataRefMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(ref));

            PointDefinition point = new PointDefinition();
            point.setId(100L);
            point.setPointCode("voltage_a");
            point.setPointName("A相电压");
            point.setUnit("V");
            when(pointDefinitionService.getById(100L)).thenReturn(point);

            List<SimulationGeneratedDataRefVO> events =
                    simulationRunService.listRunEvents(40L, CURRENT_USER_ID);
            assertThat(events).hasSize(1);
            SimulationGeneratedDataRefVO vo = events.get(0);
            assertThat(vo.getMetricSampleId()).isEqualTo(2001L);
            assertThat(vo.getPointCode()).isEqualTo("voltage_a");
            assertThat(vo.getUnit()).isEqualTo("V");
        }
    }
}
