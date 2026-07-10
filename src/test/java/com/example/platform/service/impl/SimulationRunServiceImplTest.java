package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.SimulationGeneratedDataRefVO;
import com.example.platform.dto.SimulationRunStartDTO;
import com.example.platform.dto.SimulationRunVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationGeneratedDataRef;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationGeneratedDataRefMapper;
import com.example.platform.mapper.SimulationRunRecordMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.MetricSampleService;
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
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:SimulationRunServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-01：生产门控阻断、范围拒绝、生成数据可追溯、SLEEP 步骤跳过写入、取消运行
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
    private DataScopeService dataScopeService;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private MetricSampleService metricSampleService;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SimulationRunServiceImpl simulationRunService;

    private static final Long CURRENT_USER_ID = 30L;
    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 999L;

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

    private DataScopeContext orgAdminContext(Long orgId) {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("ORG_ADMIN")
                .organizationIds(Set.of(orgId))
                .scopeType("ORG_ALL")
                .build();
    }

    private SimulationScene buildEnabledScene(Long id, Long orgId) {
        SimulationScene scene = new SimulationScene();
        scene.setId(id);
        scene.setSceneCode("SCENE_" + id);
        scene.setSceneName("场景" + id);
        scene.setOrganizationId(orgId);
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
    @DisplayName("startRun - 范围与执行")
    class StartRunExecute {

        @Test
        @DisplayName("场景已停用拒绝启动")
        void should_reject_when_sceneDisabled() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L, ORG_ID);
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
                    .thenReturn(buildEnabledScene(1L, ORG_ID));
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            assertThatThrownBy(() -> simulationRunService.startRun(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不包含任何步骤");
        }

        @Test
        @DisplayName("成功执行 WRITE_SAMPLE 步骤并记录 metric_sample_id 可追溯")
        void should_executeWriteAndRecordRef() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L, ORG_ID);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID)).thenReturn(scene);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            SimulationSceneStep step = buildWriteStep(10L, 1L, 5, 100L);
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(step));

            // 设备范围校验通过
            Device device = new Device();
            device.setId(5);
            device.setOrganizationId(ORG_ID);
            when(deviceScopeHelper.ensureDeviceAccess(eq(5), eq(CURRENT_USER_ID)))
                    .thenReturn(device);

            // metric_sample 写入成功并返回带 id 的对象
            MetricSample savedSample = new MetricSample();
            savedSample.setId(2001L);
            savedSample.setDeviceId(5);
            savedSample.setPointId(100L);
            savedSample.setSampleValue(new BigDecimal("220.5"));
            when(metricSampleService.saveSample(any(MetricSampleDTO.class), eq(CURRENT_USER_ID)))
                    .thenReturn(savedSample);

            // run 记录写入并取回
            when(simulationRunRecordMapper.insert((SimulationRunRecord) any())).thenAnswer(inv -> {
                SimulationRunRecord r = inv.getArgument(0);
                r.setId(7L);
                return 1;
            });
            when(simulationRunRecordMapper.updateById((SimulationRunRecord) any())).thenReturn(1);
            when(simulationGeneratedDataRefMapper.insert((SimulationGeneratedDataRef) any())).thenReturn(1);
            when(simulationRunRecordMapper.selectById(7L)).thenAnswer(inv -> {
                SimulationRunRecord r = new SimulationRunRecord();
                r.setId(7L);
                r.setSceneId(1L);
                r.setOrganizationId(ORG_ID);
                r.setStatus("SUCCESS");
                r.setTotalSteps(1);
                r.setSuccessSteps(1);
                return r;
            });

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);
            dto.setTargetDeviceId(5);

            SimulationRunVO vo = simulationRunService.startRun(dto, CURRENT_USER_ID);
            assertThat(vo.getStatus()).isEqualTo("SUCCESS");
            assertThat(vo.getSuccessSteps()).isEqualTo(1);
            verify(metricSampleService, times(1)).saveSample(any(MetricSampleDTO.class), eq(CURRENT_USER_ID));
            verify(simulationGeneratedDataRefMapper, times(1)).insert((SimulationGeneratedDataRef) any());
        }

        @Test
        @DisplayName("SLEEP 步骤不调用 MetricSampleService 写入")
        void should_skipWriteForSleep() {
            ReflectionTestUtils.setField(simulationRunService, "writeEnabled", true);
            SimulationScene scene = buildEnabledScene(1L, ORG_ID);
            when(simulationSceneService.getSceneOrThrow(1L, CURRENT_USER_ID)).thenReturn(scene);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            SimulationSceneStep sleepStep = new SimulationSceneStep();
            sleepStep.setId(11L);
            sleepStep.setSceneId(1L);
            sleepStep.setStepOrder(1);
            sleepStep.setPointId(100L);
            sleepStep.setTargetDeviceId(5);
            sleepStep.setAction("SLEEP");
            sleepStep.setDelayMs(0);
            when(simulationSceneStepMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(sleepStep));

            // 设备范围校验通过
            Device device = new Device();
            device.setId(5);
            device.setOrganizationId(ORG_ID);
            when(deviceScopeHelper.ensureDeviceAccess(eq(5), eq(CURRENT_USER_ID)))
                    .thenReturn(device);

            when(simulationRunRecordMapper.insert((SimulationRunRecord) any())).thenAnswer(inv -> {
                SimulationRunRecord r = inv.getArgument(0);
                r.setId(8L);
                return 1;
            });
            when(simulationRunRecordMapper.updateById((SimulationRunRecord) any())).thenReturn(1);
            when(simulationGeneratedDataRefMapper.insert((SimulationGeneratedDataRef) any())).thenReturn(1);
            when(simulationRunRecordMapper.selectById(8L)).thenAnswer(inv -> {
                SimulationRunRecord r = new SimulationRunRecord();
                r.setId(8L);
                r.setSceneId(1L);
                r.setOrganizationId(ORG_ID);
                r.setStatus("SUCCESS");
                return r;
            });

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);
            dto.setTargetDeviceId(5);

            SimulationRunVO vo = simulationRunService.startRun(dto, CURRENT_USER_ID);
            assertThat(vo.getStatus()).isEqualTo("SUCCESS");
            verify(metricSampleService, never()).saveSample(any(MetricSampleDTO.class), anyLong());
            verify(simulationGeneratedDataRefMapper, times(1)).insert((SimulationGeneratedDataRef) any());
        }

        @Test
        @DisplayName("越权访问其他组织的运行记录拒绝")
        void should_reject_when_runOutOfScope() {
            SimulationRunRecord run = new SimulationRunRecord();
            run.setId(20L);
            run.setOrganizationId(OTHER_ORG_ID);
            when(simulationRunRecordMapper.selectById(20L)).thenReturn(run);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> simulationRunService.getRunDetail(20L, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
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
            run.setOrganizationId(ORG_ID);
            run.setStatus("SUCCESS");
            when(simulationRunRecordMapper.selectById(30L)).thenReturn(run);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            SimulationRunStartDTO dto = new SimulationRunStartDTO();
            dto.setSceneId(1L);

            assertThatThrownBy(() -> simulationRunService.cancelRun(30L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已结束");
        }

        @Test
        @DisplayName("成功取消运行中状态")
        void should_cancelRunning() {
            SimulationRunRecord run = new SimulationRunRecord();
            run.setId(31L);
            run.setOrganizationId(ORG_ID);
            run.setStatus("RUNNING");
            when(simulationRunRecordMapper.selectById(31L)).thenReturn(run);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(simulationRunRecordMapper.updateById((SimulationRunRecord) any())).thenReturn(1);

            SimulationRunVO vo = simulationRunService.cancelRun(31L, CURRENT_USER_ID);
            assertThat(vo.getStatus()).isEqualTo("CANCELLED");
            verify(simulationRunRecordMapper, times(1)).updateById((SimulationRunRecord) any());
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
            run.setOrganizationId(ORG_ID);
            when(simulationRunRecordMapper.selectById(40L)).thenReturn(run);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

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
