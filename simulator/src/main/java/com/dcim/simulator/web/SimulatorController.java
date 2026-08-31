package com.dcim.simulator.web;

import com.dcim.simulator.device.*;
import com.dcim.simulator.lifecycle.SimulatorLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仿真器管理 REST：虚拟设备 / 环境档案与通道 / 观测
 */
@RestController
@RequestMapping("/api/sim")
public class SimulatorController {

    private final VirtualDeviceRepository deviceRepository;
    private final RegisterMapEntryRepository registerMapRepository;
    private final RoomEnvironmentProfileRepository profileRepository;
    private final EnvironmentChannelRepository channelRepository;
    private final DeviceRuntimeManager deviceRuntimeManager;
    private final SimulatorLifecycleService lifecycle;
    private final com.dcim.simulator.gateway.GatewayRuntime gatewayRuntime;
    private final com.dcim.simulator.gateway.GatewayConfigRepository gatewayConfigRepository;
    private final RoomPhysicsEngine physicsEngine;

    public SimulatorController(VirtualDeviceRepository deviceRepository,
                               RegisterMapEntryRepository registerMapRepository,
                               RoomEnvironmentProfileRepository profileRepository,
                               EnvironmentChannelRepository channelRepository,
                               DeviceRuntimeManager deviceRuntimeManager,
                               SimulatorLifecycleService lifecycle,
                               com.dcim.simulator.gateway.GatewayRuntime gatewayRuntime,
                               com.dcim.simulator.gateway.GatewayConfigRepository gatewayConfigRepository,
                               RoomPhysicsEngine physicsEngine) {
        this.deviceRepository = deviceRepository;
        this.registerMapRepository = registerMapRepository;
        this.profileRepository = profileRepository;
        this.channelRepository = channelRepository;
        this.deviceRuntimeManager = deviceRuntimeManager;
        this.lifecycle = lifecycle;
        this.gatewayRuntime = gatewayRuntime;
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.physicsEngine = physicsEngine;
    }

    // ==================== 虚拟设备 ====================

    @Data
    public static class CreateDeviceRequest {
        @NotBlank
        private String deviceCode;
        @NotNull
        private Long cloudRoomId;
        @NotNull
        private Integer typeId;
        private String deviceName;
        private String location;
        private Double noiseAmplitude;
        /** 设备预设ID（DevicePresetCatalog）：registers 为空时应用预设指标映射并补齐环境通道 */
        private String presetId;
        /** 指标映射：[{pointCode, registerAddr, scaleFactor}] */
        private List<RegisterMapRequest> registers;
    }

    @Data
    public static class RegisterMapRequest {
        @NotBlank
        private String pointCode;
        @NotNull
        private Integer registerAddr;
        private Double scaleFactor;
    }

    @GetMapping("/devices")
    public List<VirtualDevice> listDevices() {
        return deviceRepository.findAll();
    }

    /** 设备预设目录：5 类平台设备的标准指标映射与环境通道默认值（管理页一键创建数据源） */
    @GetMapping("/device-presets")
    public List<DevicePresetCatalog.DevicePreset> listDevicePresets() {
        return DevicePresetCatalog.PRESETS;
    }

    @PostMapping("/devices")
    public ResponseEntity<?> createDevice(@RequestBody CreateDeviceRequest request) {
        // 显式校验给出可行动的错误信息（@Valid 会抛无 message 的默认 400，管理页只能显示 Bad Request）
        if (request.getDeviceCode() == null || request.getDeviceCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备编码不能为空"));
        }
        if (request.getCloudRoomId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "必须选择机房"));
        }
        if (request.getTypeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "必须选择设备类型"));
        }
        String deviceCode = request.getDeviceCode().trim();
        if (deviceRepository.findByDeviceCode(deviceCode).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备编码已存在"));
        }
        VirtualDevice device = createDeviceCore(deviceCode, request.getCloudRoomId(),
                request.getTypeId(), request.getDeviceName(), request.getPresetId(),
                request.getRegisters(), request.getNoiseAmplitude(), request.getLocation());
        return ResponseEntity.ok(device);
    }

    /** 单台设备创建核心（单个创建与批量套件共用）：应用预设指标映射/环境通道并启动 slave */
    private VirtualDevice createDeviceCore(String deviceCode, Long cloudRoomId, Integer typeId,
                                           String deviceName, String presetId,
                                           List<RegisterMapRequest> manualRegisters,
                                           Double noiseAmplitude, String location) {
        DevicePresetCatalog.DevicePreset preset = DevicePresetCatalog.byId(presetId);
        List<RegisterMapRequest> registers = manualRegisters;
        if ((registers == null || registers.isEmpty()) && preset != null) {
            // 预设兜底：未手工填映射时应用预设指标映射
            registers = preset.registers().stream().map(r -> {
                RegisterMapRequest item = new RegisterMapRequest();
                item.setPointCode(r.pointCode());
                item.setRegisterAddr(r.registerAddr());
                item.setScaleFactor(r.scaleFactor());
                return item;
            }).toList();
        }
        VirtualDevice device = new VirtualDevice();
        device.setDeviceCode(deviceCode);
        device.setDeviceName(deviceName != null && !deviceName.isBlank() ? deviceName : deviceCode);
        device.setTypeId(typeId);
        device.setLocation(location);
        device.setCloudRoomId(cloudRoomId);
        device.setModbusPort(lifecycle.nextAvailablePort());
        device.setNoiseAmplitude(noiseAmplitude == null ? 0.0 : noiseAmplitude);
        device.setStatus(0);
        deviceRepository.save(device);

        if (registers != null) {
            for (RegisterMapRequest r : registers) {
                // 空指标行（用户点了添加没填编码）直接跳过，不落垃圾映射
                if (r.getPointCode() == null || r.getPointCode().isBlank()) {
                    continue;
                }
                RegisterMapEntry entry = new RegisterMapEntry();
                entry.setDeviceId(device.getId());
                entry.setPointCode(r.getPointCode());
                entry.setRegisterAddr(r.getRegisterAddr());
                entry.setScaleFactor(r.getScaleFactor() == null ? null
                        : java.math.BigDecimal.valueOf(r.getScaleFactor()));
                if (entry.getScaleFactor() == null) {
                    entry.setScaleFactor(java.math.BigDecimal.TEN);
                }
                registerMapRepository.save(entry);
            }
        }
        if (preset != null && !preset.channels().isEmpty()) {
            ensurePresetChannels(cloudRoomId, preset);
        }
        lifecycle.restartDevice(device);
        return device;
    }

    // ==================== 批量设备套件（多机房工厂） ====================

    @Data
    public static class DeviceSuiteRequest {
        private Integer tempHumidity;
        private Integer ups;
        private Integer pduMeter;
        private Integer precisionAc;
        private Integer leakDetector;
        /** 设备编码前缀（如 DC1 → DC1-TH01）；默认 ROOM{id} */
        private String codePrefix;
        /** 环境档案种子（档案不存在时新建用） */
        private Long seed;
    }

    /**
     * 批量创建一个机房的整套虚拟设备：逐台应用预设（寄存器+通道），编码冲突跳过不中断
     */
    @PostMapping("/rooms/{cloudRoomId}/device-suite")
    public ResponseEntity<?> createDeviceSuite(@PathVariable Long cloudRoomId,
                                               @RequestBody DeviceSuiteRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "请求体不能为空"));
        }
        String prefix = (request.getCodePrefix() == null || request.getCodePrefix().isBlank())
                ? "ROOM" + cloudRoomId : request.getCodePrefix().trim();
        if (prefix.length() > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "编码前缀过长（≤20 字符）"));
        }
        // 环境档案先行：不存在则按种子建默认档案（温湿度/空调预设不带通道，依赖档案默认通道）
        ensureProfile(cloudRoomId, request.getSeed() == null ? 42L : request.getSeed());

        // 类型顺序固定，编码缩写与 typeId 对齐 DevicePresetCatalog；
        // 预设按 typeId 解析（套件不传 presetId，逐台应用预设指标映射与环境通道）
        java.util.List<int[]> plan = new java.util.ArrayList<>();
        if (request.getTempHumidity() != null) plan.add(new int[]{4, request.getTempHumidity()});
        if (request.getPrecisionAc() != null) plan.add(new int[]{3, request.getPrecisionAc()});
        if (request.getUps() != null) plan.add(new int[]{1, request.getUps()});
        if (request.getPduMeter() != null) plan.add(new int[]{2, request.getPduMeter()});
        if (request.getLeakDetector() != null) plan.add(new int[]{5, request.getLeakDetector()});
        java.util.Map<Integer, String> abbr = Map.of(4, "TH", 3, "AC", 1, "UPS", 2, "PDU", 5, "LEAK");
        java.util.Map<Integer, String> presetByType = new HashMap<>();
        for (DevicePresetCatalog.DevicePreset p : DevicePresetCatalog.PRESETS) {
            presetByType.putIfAbsent(p.typeId(), p.presetId());
        }

        int created = 0;
        List<Map<String, String>> skipped = new java.util.ArrayList<>();
        for (int[] typePlan : plan) {
            int typeId = typePlan[0];
            for (int i = 1; i <= typePlan[1]; i++) {
                String code = prefix + "-" + abbr.get(typeId) + String.format("%02d", i);
                if (deviceRepository.findByDeviceCode(code).isPresent()) {
                    skipped.add(Map.of("deviceCode", code, "reason", "设备编码已存在"));
                    continue;
                }
                createDeviceCore(code, cloudRoomId, typeId, code, presetByType.get(typeId), null, 0.0, null);
                created++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("cloudRoomId", cloudRoomId);
        return ResponseEntity.ok(result);
    }

    /** 确保机房环境档案存在（不存在则按种子新建并配默认通道） */
    private void ensureProfile(Long cloudRoomId, Long seed) {
        profileRepository.findByCloudRoomId(cloudRoomId).orElseGet(() -> {
            RoomEnvironmentProfile profile = new RoomEnvironmentProfile();
            profile.setCloudRoomId(cloudRoomId);
            profile.setSeed(seed);
            profile = profileRepository.save(profile);
            createDefaultChannels(profile.getId());
            return profile;
        });
    }

    // 预设环境通道补齐：档案不存在则先建默认档案；同指标通道已存在时不覆盖（保留已调参值）
    private void ensurePresetChannels(Long cloudRoomId, DevicePresetCatalog.DevicePreset preset) {
        RoomEnvironmentProfile profile = profileRepository.findByCloudRoomId(cloudRoomId)
                .orElseGet(() -> {
                    RoomEnvironmentProfile created = new RoomEnvironmentProfile();
                    created.setCloudRoomId(cloudRoomId);
                    created.setSeed(42L);
                    created = profileRepository.save(created);
                    createDefaultChannels(created.getId());
                    return created;
                });
        for (DevicePresetCatalog.ChannelSpec spec : preset.channels()) {
            if (channelRepository.findByProfileIdAndPointCode(profile.getId(), spec.pointCode()).isEmpty()) {
                saveChannel(profile.getId(), spec.pointCode(), spec.funcType(),
                        String.valueOf(spec.base()), spec.amplitude() == null ? null : String.valueOf(spec.amplitude()),
                        spec.periodSec() == null ? null : String.valueOf(spec.periodSec()),
                        spec.slopePerSec() == null ? null : String.valueOf(spec.slopePerSec()), null);
            }
        }
    }


    @PutMapping("/devices/{id}/fault")
    public ResponseEntity<?> injectFault(@PathVariable Long id, @RequestParam String mode) {
        VirtualDevice device = deviceRepository.findById(id).orElse(null);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        if (!List.of("NONE", "SENSOR_STUCK", "SENSOR_DEAD").contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法故障模式"));
        }
        device.setFaultMode(mode);
        deviceRepository.save(device);
        deviceRuntimeManager.applyFault(id, mode);
        return ResponseEntity.ok(device);
    }

    @PutMapping("/devices/{id}/status")
    public ResponseEntity<?> toggleDevice(@PathVariable Long id, @RequestParam Integer status) {
        VirtualDevice device = deviceRepository.findById(id).orElse(null);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        device.setStatus(status);
        deviceRepository.save(device);
        if (status == 1) {
            deviceRuntimeManager.stopDevice(id);
        } else {
            lifecycle.restartDevice(device);
        }
        return ResponseEntity.ok(device);
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<?> deleteDevice(@PathVariable Long id) {
        if (deviceRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        deviceRuntimeManager.stopDevice(id);
        registerMapRepository.deleteAll(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(id));
        deviceRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ==================== 环境档案与通道 ====================

    @PostMapping("/profiles")
    public RoomEnvironmentProfile createProfile(@RequestParam Long cloudRoomId, @RequestParam Long seed,
                                                @RequestParam(defaultValue = "true") boolean defaultChannels) {
        return profileRepository.findByCloudRoomId(cloudRoomId)
                .orElseGet(() -> {
                    RoomEnvironmentProfile profile = new RoomEnvironmentProfile();
                    profile.setCloudRoomId(cloudRoomId);
                    profile.setSeed(seed);
                    profile = profileRepository.save(profile);
                    if (defaultChannels) {
                        createDefaultChannels(profile.getId());
                    }
                    return profile;
                });
    }

    // 默认环境通道：仅累计电量（温湿度/功率等室内环境指标由环境物理引擎接管）
    private void createDefaultChannels(Long profileId) {
        // 累计电量：RAMP 斜率 = 平均功率80kW / 3600s = 每3600秒累积80kWh（kWh/秒粒度）
        saveChannel(profileId, "power_kwh", "RAMP", "0", null, null, "0.0222", null);
    }

    private void saveChannel(Long profileId, String pointCode, String funcType, String base,
                             String amplitude, String periodSec, String slopePerSec, String stepTo) {
        EnvironmentChannel channel = new EnvironmentChannel();
        channel.setProfileId(profileId);
        channel.setPointCode(pointCode);
        channel.setFuncType(funcType);
        channel.setBaseValue(new java.math.BigDecimal(base));
        if (amplitude != null) channel.setAmplitude(new java.math.BigDecimal(amplitude));
        if (periodSec != null) channel.setPeriodSec(Integer.parseInt(periodSec));
        if (slopePerSec != null) channel.setSlopePerSec(new java.math.BigDecimal(slopePerSec));
        if (stepTo != null) {
            channel.setStepTo(new java.math.BigDecimal(stepTo));
            channel.setStepAt(java.time.LocalDateTime.now());
        }
        channelRepository.save(channel);
    }

    @GetMapping("/profiles/{cloudRoomId}/channels")
    public List<EnvironmentChannel> listChannels(@PathVariable Long cloudRoomId) {
        return profileRepository.findByCloudRoomId(cloudRoomId)
                .map(p -> channelRepository.findByProfileId(p.getId()))
                .orElse(List.of());
    }

    @PostMapping("/profiles/{cloudRoomId}/channels")
    public ResponseEntity<?> upsertChannel(@PathVariable Long cloudRoomId,
                                           @RequestBody EnvironmentChannel channel) {
        if (RoomPhysicsEngine.PHYSICS_POINTS.contains(channel.getPointCode())) {
            // 物理接管指标拒绝手工通道：影响环境请调 IT 负载/空调设定
            return ResponseEntity.badRequest().body(Map.of("error",
                    "指标 " + channel.getPointCode() + " 由环境物理引擎驱动，请调整 IT 负载或空调设定"));
        }
        RoomEnvironmentProfile profile = profileRepository.findByCloudRoomId(cloudRoomId)
                .orElseGet(() -> {
                    RoomEnvironmentProfile p = new RoomEnvironmentProfile();
                    p.setCloudRoomId(cloudRoomId);
                    p.setSeed(42L);
                    return profileRepository.save(p);
                });
        channelRepository.deleteByProfileIdAndPointCode(profile.getId(), channel.getPointCode());
        channel.setId(null);
        channel.setProfileId(profile.getId());
        // STEP 通道未显式指定阶跃时刻时立即生效
        if ("STEP".equals(channel.getFuncType()) && channel.getStepTo() != null
                && channel.getStepAt() == null) {
            channel.setStepAt(LocalDateTime.now());
        }
        return ResponseEntity.ok(channelRepository.saveAndFlush(channel));
    }

    // ==================== 环境物理引擎 ====================

    /** 机房环境实时快照：室外天气 + 室内热湿状态 + 空调状态与参数 */
    @GetMapping("/profiles/{cloudRoomId}/physics")
    public ResponseEntity<?> physics(@PathVariable Long cloudRoomId) {
        RoomPhysicsEngine.RoomSnapshot snapshot = physicsEngine.snapshotOf(cloudRoomId).orElse(null);
        if (snapshot == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "机房未初始化环境档案"));
        }
        return ResponseEntity.ok(snapshot);
    }

    /** 更新机房环境参数（IT 负载/空调制冷量与设定），传哪个改哪个 */
    @PutMapping("/profiles/{cloudRoomId}/params")
    public ResponseEntity<?> updateParams(@PathVariable Long cloudRoomId,
                                          @RequestBody RoomEnvironmentProfile params) {
        RoomEnvironmentProfile profile = profileRepository.findByCloudRoomId(cloudRoomId).orElse(null);
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "机房未初始化环境档案"));
        }
        if (params.getItLoadKw() != null) profile.setItLoadKw(clampParam(params.getItLoadKw(), 0, 500));
        if (params.getAcRatedKw() != null) profile.setAcRatedKw(clampParam(params.getAcRatedKw(), 0, 500));
        if (params.getAcSetpointC() != null) profile.setAcSetpointC(clampParam(params.getAcSetpointC(), 16, 35));
        if (params.getAcSetpointRh() != null) profile.setAcSetpointRh(clampParam(params.getAcSetpointRh(), 20, 90));
        profileRepository.save(profile);
        return ResponseEntity.ok(Map.of("message", "环境参数已更新"));
    }

    /** 环境演练：AC_OFF/AC_ON 空调停机恢复、FIRE 火灾热注入、IT_SPIKE 负载激增（梯形波）、
     *  NATURAL_COLD_SNAP/NATURAL_HEAT_WAVE/NATURAL_PROLONGED_RAIN 人工自然事件——均为流/参数作用 */
    @PostMapping("/profiles/{cloudRoomId}/physics-actions")
    public ResponseEntity<?> physicsAction(@PathVariable Long cloudRoomId,
                                           @RequestParam String mode,
                                           @RequestParam(defaultValue = "120") int durationSec,
                                           @RequestParam(defaultValue = "30") double deltaKw) {
        String message;
        switch (mode) {
            case "AC_OFF" -> {
                physicsEngine.setAcEnabled(cloudRoomId, false);
                message = "空调已停机：机房将随 IT 发热自然升温";
            }
            case "AC_ON" -> {
                physicsEngine.setAcEnabled(cloudRoomId, true);
                message = "空调已恢复：机房将回到设定温度";
            }
            case "FIRE" -> {
                physicsEngine.fireDrill(cloudRoomId, durationSec);
                message = "火灾演练已注入（" + durationSec + "s），温度可越常规上限";
            }
            case "IT_SPIKE" -> {
                physicsEngine.itSpikeDrill(cloudRoomId, deltaKw, durationSec);
                message = "负载激增 +" + deltaKw + "kW（10min 爬坡，保持 " + durationSec + "s 后卸坡）";
            }
            case "NATURAL_COLD_SNAP", "NATURAL_HEAT_WAVE", "NATURAL_PROLONGED_RAIN" -> {
                String kind = mode.substring("NATURAL_".length());
                physicsEngine.naturalEventDrill(cloudRoomId, kind, durationSec);
                message = "人工自然事件已注入（" + durationSec / 60 + " 分钟，边界缓动生效）";
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "非法演练模式"));
            }
        }
        return ResponseEntity.ok(Map.of("message", message));
    }

    private Double clampParam(Double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 环境快捷操作 ====================

    /** 快捷操作：TRANSITION 平滑调整某指标到目标值（如温度拉到45℃演练） */
    @PostMapping("/profiles/{cloudRoomId}/quick-actions")
    public ResponseEntity<?> quickAction(@PathVariable Long cloudRoomId,
                                         @RequestParam String pointCode,
                                         @RequestParam double targetValue,
                                         @RequestParam(defaultValue = "120") int durationSec) {
        if (RoomPhysicsEngine.PHYSICS_POINTS.contains(pointCode)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "指标 " + pointCode + " 由环境物理引擎驱动，请使用空调停机/火灾演练"));
        }
        RoomEnvironmentProfile profile = profileRepository.findByCloudRoomId(cloudRoomId).orElse(null);
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "机房未初始化环境档案"));
        }
        channelRepository.deleteByProfileIdAndPointCode(profile.getId(), pointCode);
        EnvironmentChannel channel = new EnvironmentChannel();
        channel.setProfileId(profile.getId());
        channel.setPointCode(pointCode);
        channel.setFuncType("TRANSITION");
        channel.setBaseValue(currentValueOf(profile.getId(), pointCode));
        channel.setStepTo(java.math.BigDecimal.valueOf(targetValue));
        channel.setStepAt(LocalDateTime.now());
        channel.setDurationSec(durationSec);
        channelRepository.save(channel);
        return ResponseEntity.ok(Map.of("message",
                pointCode + " 将在 " + durationSec + " 秒内平滑过渡到 " + targetValue));
    }

    private java.math.BigDecimal currentValueOf(Long profileId, String pointCode) {
        return EnvironmentEngine.evaluate(
                channelRepository.findByProfileIdAndPointCode(profileId, pointCode),
                LocalDateTime.now());
    }

    // ==================== 观测 ====================

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "runningSlaves", deviceRuntimeManager.runningServerCount(),
                "gatewayOnline", gatewayRuntime.snapshot().size() > 0,
                "latestValues", gatewayRuntime.snapshot(),
                "offlineDevices", gatewayRuntime.offlineDevices(),
                "serverTime", LocalDateTime.now().toString());
    }
}
