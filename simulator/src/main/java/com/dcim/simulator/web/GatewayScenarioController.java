package com.dcim.simulator.web;

import com.dcim.simulator.gateway.GatewayConfig;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.scenario.Scenario;
import com.dcim.simulator.scenario.ScenarioPlayer;
import com.dcim.simulator.scenario.ScenarioRepository;
import com.dcim.simulator.scenario.ScenarioStep;
import com.dcim.simulator.scenario.ScenarioStepRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * 仿真器管理 REST：网关配置 / 剧本 / 平台对接
 */
@RestController
@RequestMapping("/api/sim")
public class GatewayScenarioController {

    // 平台设备类型码映射（device_types.type_code）
    private static final java.util.Map<Integer, String> TYPE_CODES = java.util.Map.of(
            1, "UPS", 2, "PDU_METER", 3, "PRECISION_AC", 4, "TEMP_HUMIDITY", 5, "LEAK_DETECTOR");

    private final GatewayConfigRepository configRepository;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository stepRepository;
    private final ScenarioPlayer scenarioPlayer;
    private final com.dcim.simulator.device.VirtualDeviceRepository deviceRepository;
    private final com.dcim.simulator.device.RegisterMapEntryRepository registerMapRepository;
    private final com.dcim.simulator.gateway.GatewayRuntime gatewayRuntime;
    private final PlatformDirectoryCache directoryCache;

    @org.springframework.beans.factory.annotation.Value("${gateway.platform-base-url:http://localhost:8080}")
    private String platformBaseUrl;

    public GatewayScenarioController(GatewayConfigRepository configRepository,
                                     ScenarioRepository scenarioRepository,
                                     ScenarioStepRepository stepRepository,
                                     ScenarioPlayer scenarioPlayer,
                                     com.dcim.simulator.device.VirtualDeviceRepository deviceRepository,
                                     com.dcim.simulator.device.RegisterMapEntryRepository registerMapRepository,
                                     com.dcim.simulator.gateway.GatewayRuntime gatewayRuntime,
                                     PlatformDirectoryCache directoryCache) {
        this.configRepository = configRepository;
        this.deviceRepository = deviceRepository;
        this.registerMapRepository = registerMapRepository;
        this.gatewayRuntime = gatewayRuntime;
        this.directoryCache = directoryCache;
        this.scenarioRepository = scenarioRepository;
        this.stepRepository = stepRepository;
        this.scenarioPlayer = scenarioPlayer;
    }

    // ==================== 网关配置（多实例） ====================

    /**
     * 网关列表（含运行状态与本机房设备数）
     */
    @GetMapping("/gateways")
    public List<Map<String, Object>> listGateways() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GatewayConfig config : configRepository.findAll()) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", config.getId());
            item.put("name", config.getName());
            item.put("cloudRoomId", config.getCloudRoomId());
            item.put("northboundTransport", config.getNorthboundTransport());
            item.put("platformBaseUrl", config.getPlatformBaseUrl());
            item.put("pollIntervalSec", config.getPollIntervalSec());
            item.put("reportIntervalSec", config.getReportIntervalSec());
            item.put("changeDeadband", config.getChangeDeadband());
            item.put("alarmReportIntervalSec", config.getAlarmReportIntervalSec());
            item.put("status", config.getStatus());
            item.put("running", gatewayRuntime.isRunning(config.getId()));
            item.put("deviceCount", countRoomDevices(config.getCloudRoomId()));
            result.add(item);
        }
        return result;
    }

    /** 压测观测：各网关客户端统计（qps/成功率/延迟分位） */
    @GetMapping("/load-stats")
    public List<Map<String, Object>> loadStats() {
        var statsById = gatewayRuntime.loadStats();
        List<Map<String, Object>> result = new ArrayList<>();
        for (GatewayConfig config : configRepository.findAll()) {
            var stats = statsById.get(config.getId());
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("gatewayId", config.getId());
            item.put("name", config.getName());
            item.put("cloudRoomId", config.getCloudRoomId());
            item.put("running", gatewayRuntime.isRunning(config.getId()));
            item.put("pollIntervalSec", config.getPollIntervalSec());
            item.put("reportIntervalSec", config.getReportIntervalSec());
            if (stats != null) {
                item.put("httpRequests", stats.httpRequests());
                item.put("httpSuccess", stats.httpSuccess());
                item.put("httpFailed", stats.httpFailed());
                item.put("qps", Math.round(stats.qps10s() * 100) / 100.0);
                item.put("p50Ms", stats.percentile(0.50));
                item.put("p95Ms", stats.percentile(0.95));
                item.put("p99Ms", stats.percentile(0.99));
            }
            result.add(item);
        }
        return result;
    }

    private long countRoomDevices(Long cloudRoomId) {
        return deviceRepository.findAll().stream()
                .filter(d -> java.util.Objects.equals(d.getCloudRoomId(), cloudRoomId))
                .count();
    }

    /**
     * 创建网关（cloudRoomId 必填且不得与其他网关重复；-1 表示占位待绑定）
     */
    @PostMapping("/gateways")
    public ResponseEntity<?> createGateway(@RequestBody GatewayConfig config) {
        config.setId(null);
        resolvePlatformBaseUrl(config);
        clampIntervals(config);
        ResponseEntity<?> error = validateRoomBinding(config);
        if (error != null) {
            return error;
        }
        config = configRepository.save(config);
        if (config.getStatus() != null && config.getStatus() == 0) {
            gatewayRuntime.start(config);
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 编辑网关（热重载；启停随 status 联动）
     */
    @PutMapping("/gateways/{id}")
    public ResponseEntity<?> updateGateway(@PathVariable Long id, @RequestBody GatewayConfig body) {
        GatewayConfig merged = configRepository.findById(id).orElse(null);
        if (merged == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关不存在: " + id));
        }
        // 部分更新语义：null 字段保持原值（列表响应不回传密钥；压测档位等
        // 最小 body 只改周期参数，凭证/机房/名称全部保留）
        if (body.getName() != null && !body.getName().isBlank()) {
            merged.setName(body.getName());
        }
        if (body.getNorthboundTransport() != null) {
            merged.setNorthboundTransport(body.getNorthboundTransport());
        }
        if (body.getPlatformBaseUrl() != null && !body.getPlatformBaseUrl().isBlank()) {
            merged.setPlatformBaseUrl(body.getPlatformBaseUrl());
        }
        if (body.getIngestAccessKey() != null && !body.getIngestAccessKey().isBlank()) {
            merged.setIngestAccessKey(body.getIngestAccessKey());
        }
        if (body.getIngestSecret() != null && !body.getIngestSecret().isBlank()) {
            merged.setIngestSecret(body.getIngestSecret());
        }
        if (body.getCloudRoomId() != null) {
            merged.setCloudRoomId(body.getCloudRoomId());
        }
        if (body.getPollIntervalSec() != null) {
            merged.setPollIntervalSec(body.getPollIntervalSec());
        }
        if (body.getReportIntervalSec() != null) {
            merged.setReportIntervalSec(body.getReportIntervalSec());
        }
        if (body.getChangeDeadband() != null) {
            merged.setChangeDeadband(body.getChangeDeadband());
        }
        if (body.getAlarmReportIntervalSec() != null) {
            merged.setAlarmReportIntervalSec(body.getAlarmReportIntervalSec());
        }
        if (body.getStatus() != null) {
            merged.setStatus(body.getStatus());
        }
        resolvePlatformBaseUrl(merged);
        clampIntervals(merged);
        ResponseEntity<?> error = validateRoomBinding(merged);
        if (error != null) {
            return error;
        }
        merged = configRepository.save(merged);
        reloadToMatchStatus(merged);
        return ResponseEntity.ok(merged);
    }

    /**
     * 删除网关（运行中须先停止）
     */
    @DeleteMapping("/gateways/{id}")
    public ResponseEntity<?> deleteGateway(@PathVariable Long id) {
        if (gatewayRuntime.isRunning(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关运行中，请先停止再删除"));
        }
        configRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "网关已删除"));
    }

    @PostMapping("/gateways/{id}/start")
    public ResponseEntity<?> startGateway(@PathVariable Long id) {
        GatewayConfig config = configRepository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关不存在: " + id));
        }
        config.setStatus(0);
        config = configRepository.save(config);
        gatewayRuntime.start(config);
        return ResponseEntity.ok(Map.of("message", "网关已启动", "running", true));
    }

    @PostMapping("/gateways/{id}/stop")
    public ResponseEntity<?> stopGateway(@PathVariable Long id) {
        GatewayConfig config = configRepository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关不存在: " + id));
        }
        config.setStatus(1);
        configRepository.save(config);
        gatewayRuntime.stop(id);
        return ResponseEntity.ok(Map.of("message", "网关已停止", "running", false));
    }

    private void reloadToMatchStatus(GatewayConfig config) {
        if (config.getStatus() != null && config.getStatus() == 0) {
            gatewayRuntime.start(config);
        } else {
            gatewayRuntime.stop(config.getId());
        }
    }

    /**
     * 平台地址归一化：留空回退服务端默认（gateway.platform-base-url，8080 口径）；
     * 支持用户填完整地址（可含 context path /api 与尾斜杠），统一存根地址，北向拼接 /api/ingest/*
     */
    private void resolvePlatformBaseUrl(GatewayConfig config) {
        String url = config.getPlatformBaseUrl();
        if (url == null || url.isBlank()) {
            config.setPlatformBaseUrl(platformBaseUrl);
            return;
        }
        url = url.trim().replaceAll("/+$", "");
        if (url.endsWith("/api")) {
            url = url.substring(0, url.length() - 4);
        }
        config.setPlatformBaseUrl(url);
    }

    /** 轮询/上报周期下限 1s（风暴档防误配 0 值空转） */
    private void clampIntervals(GatewayConfig config) {
        if (config.getPollIntervalSec() == null || config.getPollIntervalSec() < 1) {
            config.setPollIntervalSec(1);
        }
        if (config.getReportIntervalSec() == null || config.getReportIntervalSec() < 1) {
            config.setReportIntervalSec(1);
        }
    }

    /** 机房绑定校验：cloudRoomId 必填（-1 为占位），正数不得与其他网关重复（一机房一网关） */
    private ResponseEntity<?> validateRoomBinding(GatewayConfig config) {
        Long roomId = config.getCloudRoomId();
        if (roomId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "必须选择网关绑定的机房"));
        }
        if (roomId <= 0) {
            return null; // -1 占位（旧数据待人工绑定），允许多个
        }
        boolean duplicated = configRepository.findAll().stream()
                .anyMatch(other -> !java.util.Objects.equals(other.getId(), config.getId())
                        && roomId.equals(other.getCloudRoomId()));
        if (duplicated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "机房 #" + roomId + " 已有绑定网关（一机房一网关）"));
        }
        return null;
    }

    // ==================== 旧单网关端点（兼容保留） ====================

    @GetMapping("/gateway")
    public GatewayConfig getGateway() {
        // 无已保存配置时返回后端默认（平台地址真源在服务端配置，避免前端缓存页显示过期默认）
        return configRepository.findAll().stream().findFirst().orElseGet(this::defaultConfig);
    }

    private GatewayConfig defaultConfig() {
        GatewayConfig config = new GatewayConfig();
        config.setName("仿真网关01");
        config.setNorthboundTransport("HTTP");
        config.setPlatformBaseUrl(platformBaseUrl);
        config.setPollIntervalSec(5);
        config.setReportIntervalSec(60);
        config.setChangeDeadband(new java.math.BigDecimal("0.5"));
        config.setAlarmReportIntervalSec(5);
        config.setStatus(0);
        return config;
    }

    @PostMapping("/gateway")
    public GatewayConfig saveGateway(@RequestBody GatewayConfig config) {
        if (config.getId() != null && configRepository.existsById(config.getId())) {
            GatewayConfig merged = configRepository.findById(config.getId()).orElseThrow();
            merged.setName(config.getName());
            merged.setNorthboundTransport(config.getNorthboundTransport());
            merged.setPlatformBaseUrl(config.getPlatformBaseUrl());
            merged.setIngestAccessKey(config.getIngestAccessKey());
            merged.setIngestSecret(config.getIngestSecret());
            merged.setCloudRoomId(config.getCloudRoomId());
            merged.setPollIntervalSec(config.getPollIntervalSec());
            merged.setReportIntervalSec(config.getReportIntervalSec());
            merged.setChangeDeadband(config.getChangeDeadband());
            merged.setAlarmReportIntervalSec(config.getAlarmReportIntervalSec());
            merged.setStatus(config.getStatus());
            config = merged;
        }
        resolvePlatformBaseUrl(config);
        clampIntervals(config);
        config = configRepository.save(config);
        reloadToMatchStatus(config);
        return config;
    }

    // ==================== 剧本 ====================

    @GetMapping("/scenarios")
    public List<Scenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    @PostMapping("/scenarios")
    public Scenario createScenario(@RequestBody Scenario scenario) {
        scenario.setId(null);
        return scenarioRepository.save(scenario);
    }

    @GetMapping("/scenarios/{id}/steps")
    public List<ScenarioStep> listSteps(@PathVariable Long id) {
        return stepRepository.findByScenarioIdOrderByStepOrder(id);
    }

    @PostMapping("/scenarios/{id}/steps")
    public ScenarioStep addStep(@PathVariable Long id, @RequestBody ScenarioStep step) {
        step.setId(null);
        step.setScenarioId(id);
        return stepRepository.save(step);
    }

    @PostMapping("/scenarios/{id}/play")
    public ResponseEntity<?> play(@PathVariable Long id) {
        return scenarioPlayer.play(id)
                ? ResponseEntity.ok(Map.of("message", "剧本播放已启动"))
                : ResponseEntity.badRequest().body(Map.of("error", "剧本不存在或无步骤"));
    }

    @PostMapping("/scenarios/stop")
    public ResponseEntity<?> stop() {
        scenarioPlayer.stop();
        return ResponseEntity.ok(Map.of("message", "剧本播放已停止"));
    }

    // ==================== 平台对接（按网关） ====================

    /** 设备清单同步：汇总本机房虚拟设备调用平台 POST /ingest/inventory（Gateway 签名） */
    @PostMapping("/platform/sync-inventory")
    public ResponseEntity<?> syncInventory(@RequestParam(required = false) Long gatewayId) {
        GatewayConfig gw = resolveGateway(gatewayId);
        if (gw == null || gw.getIngestAccessKey() == null || gw.getIngestAccessKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关未配置接入凭证"));
        }
        // 清单只含本机房设备（跨机房设备用本机房凭证上报必然被平台归属校验拒绝）
        java.util.List<com.dcim.simulator.device.VirtualDevice> roomDevices =
                gw.getCloudRoomId() == null
                        ? deviceRepository.findAll()
                        : deviceRepository.findAll().stream()
                                .filter(d -> gw.getCloudRoomId().equals(d.getCloudRoomId()))
                                .toList();
        StringBuilder body = new StringBuilder("{\"devices\":[");
        boolean first = true;
        for (var device : roomDevices) {
            List<String> points = registerMapRepository
                    .findByDeviceIdOrderByRegisterAddrAsc(device.getId())
                    .stream().map(com.dcim.simulator.device.RegisterMapEntry::getPointCode)
                    .distinct().toList();
            // 手工拼 JSON 数组（List.toString 的引号会被外层转义破坏）
            StringBuilder pointJson = new StringBuilder("[");
            for (int i = 0; i < points.size(); i++) {
                if (i > 0) pointJson.append(',');
                pointJson.append('"').append(points.get(i)).append('"');
            }
            pointJson.append(']');
            if (!first) {
                body.append(',');
            }
            first = false;
            String name = device.getDeviceName() == null ? device.getDeviceCode() : device.getDeviceName();
            String typeCode = TYPE_CODES.get(device.getTypeId());
            body.append("{\"deviceCode\":\"").append(device.getDeviceCode())
                    .append("\",\"deviceName\":\"").append(name);
            if (typeCode != null) {
                body.append("\",\"deviceTypeCode\":\"").append(typeCode).append('"');
            }
            body.append(",\"pointCodes\":").append(pointJson).append('}');
        }
        body.append("]}");
        try {
            String response = org.springframework.web.client.RestClient.create().post()
                    .uri(gw.getPlatformBaseUrl() + "/api/ingest/inventory")
                    .header("Authorization", "Gateway " + gw.getIngestAccessKey() + ":"
                            + sign(gw.getIngestSecret(), body.toString()))
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve().body(String.class);
            return ResponseEntity.ok(Map.of("message", "清单已同步", "platformResponse", response));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("error", "同步失败: " + ex.getMessage()));
        }
    }

    /** 拉取平台告警阈值进网关 ON_ALARM 策略缓存（Gateway 签名导出接口，松耦合；按网关独立缓存） */
    @PostMapping("/platform/sync-thresholds")
    public ResponseEntity<?> syncThresholds(@RequestParam(required = false) Long gatewayId) {
        GatewayConfig gw = resolveGateway(gatewayId);
        if (gw == null || gw.getIngestAccessKey() == null || gw.getIngestAccessKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "网关未配置接入凭证"));
        }
        try {
            com.dcim.simulator.client.PlatformIngestClient client =
                    new com.dcim.simulator.client.PlatformIngestClient(
                            gw.getPlatformBaseUrl(), gw.getIngestAccessKey(), gw.getIngestSecret());
            Map<String, Object> export = client.exportConfig();
            Map<String, List<com.dcim.simulator.gateway.ReportStrategy.Threshold>> thresholds =
                    new HashMap<>();
            for (Map<String, Object> item : client.thresholdsOf(export)) {
                Object pointCode = item.get("pointCode");
                Object operator = item.get("operator");
                Object threshold = item.get("threshold");
                if (pointCode == null || threshold == null) {
                    continue;
                }
                thresholds.computeIfAbsent(String.valueOf(pointCode), k -> new ArrayList<>())
                        .add(new com.dcim.simulator.gateway.ReportStrategy.Threshold(
                                String.valueOf(operator),
                                new java.math.BigDecimal(String.valueOf(threshold))));
            }
            gatewayRuntime.updateThresholds(gw.getId(), thresholds);
            directoryCache.refresh(client.roomsOf(export));
            return ResponseEntity.ok(Map.of("message", "阈值已同步", "points", thresholds.size()));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("error", "同步失败: " + ex.getMessage()));
        }
    }

    /** 网关解析：gatewayId 指定优先，缺省回退第一个配置（旧单网关语义兼容） */
    private GatewayConfig resolveGateway(Long gatewayId) {
        if (gatewayId != null) {
            return configRepository.findById(gatewayId).orElse(null);
        }
        return configRepository.findAll().stream().findFirst().orElse(null);
    }

    /**
     * 启动预热：全部已配置凭证的网关各自拉取一次阈值与机房目录。
     * 阈值缓存为实例内存态（重启即失），不预热则网关本地越限即报无阈值、管理页机房下拉为空。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmPlatformCacheOnStartup() {
        for (GatewayConfig config : configRepository.findAll()) {
            if (config.getIngestAccessKey() == null || config.getIngestAccessKey().isBlank()) {
                continue;
            }
            try {
                syncThresholds(config.getId());
            } catch (Exception ex) {
                // 预热失败不阻断启动：平台未就绪/网络未通时可在管理页手动"拉取告警阈值"
            }
        }
    }

    private String sign(String secret, String payload) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 平台机房目录（供管理页机房下拉，来源为阈值导出接口附带数据） */
    @GetMapping("/platform/rooms")
    public List<Map<String, Object>> platformRooms() {
        return directoryCache.rooms();
    }

    /** 平台连通自检：Gateway 签名心跳（零管理账号依赖） */
    @PostMapping("/platform/ping")
    public ResponseEntity<?> platformPing(@RequestBody GatewayConfig probe) {
        try {
            boolean ok = new com.dcim.simulator.client.PlatformIngestClient(
                    probe.getPlatformBaseUrl(), probe.getIngestAccessKey(), probe.getIngestSecret()).ping();
            return ResponseEntity.ok(Map.of("ok", ok, "error", ok ? "" : "心跳失败，请检查地址与密钥"));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("ok", false, "error", String.valueOf(ex.getMessage())));
        }
    }
}
