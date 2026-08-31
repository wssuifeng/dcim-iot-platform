package com.dcim.simulator.gateway;

import com.dcim.simulator.device.RegisterMapEntry;
import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.VirtualDevice;
import com.dcim.simulator.device.VirtualDeviceRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 网关实例：单机房网关的全部运行态——南向轮询（FC03）、上报策略、北向连接与阈值缓存
 *
 * 说明:由 GatewayRuntime 注册表持有并按秒调度，实例间互不阻塞；
 *   只轮询/上报本机房（cloud_room_id）设备，与真实"一机房一网关一凭证"同构；
 *   cloud_room_id 为空的历史配置退化为轮询全部设备（启动回填兜底）。
 */
@Slf4j
public class GatewayInstance {

    /** 调度句柄（注册表停止实例时取消本实例的定时 tick） */
    private volatile ScheduledFuture<?> scheduledFuture;

    private final GatewayConfig config;
    private final VirtualDeviceRepository deviceRepository;
    private final RegisterMapEntryRepository registerMapRepository;
    private final ModbusMaster modbusMaster;

    private final NorthboundClient client;

    /** 最近一次成功轮询的工程量：deviceCode → pointCode → value */
    private final Map<String, Map<String, BigDecimal>> latestValues = new ConcurrentHashMap<>();
    /** 最近一次上报值（死区判定）：pointCode → value */
    private final Map<String, BigDecimal> lastReported = new ConcurrentHashMap<>();
    /** 设备离线计数：deviceCode → 连续失败次数 */
    private final Map<String, Integer> offlineStreak = new ConcurrentHashMap<>();
    /** 平台告警规则缓存（pointCode → 阈值），供越限即报判定 */
    private volatile Map<String, List<ReportStrategy.Threshold>> thresholds = Map.of();

    private LocalDateTime lastPoll;
    private LocalDateTime lastPeriodicReportAt;
    private LocalDateTime lastHeartbeatAt;

    private static final String LOCALHOST = "127.0.0.1";
    /** 平台契约单批上限（≤200 条），上报按此分片 */
    static final int REPORT_BATCH_MAX = 200;

    GatewayInstance(GatewayConfig config,
                    VirtualDeviceRepository deviceRepository,
                    RegisterMapEntryRepository registerMapRepository,
                    ModbusMaster modbusMaster) {
        this.config = config;
        this.deviceRepository = deviceRepository;
        this.registerMapRepository = registerMapRepository;
        this.modbusMaster = modbusMaster;
        this.client = new NorthboundClient(config.getNorthboundTransport(),
                config.getPlatformBaseUrl(),
                config.getIngestAccessKey(),
                config.getIngestSecret());
        log.info("网关实例启动 name={} room={} transport={}", config.getName(),
                config.getCloudRoomId(), config.getNorthboundTransport());
    }

    GatewayConfig config() {
        return config;
    }

    Long configId() {
        return config.getId();
    }

    /**
     * 每秒调度入口：按各自配置周期执行轮询/上报/心跳
     */
    void tick() {
        if (config.getStatus() == null || config.getStatus() != 0) {
            return;
        }
        // 以固定节拍近似配置周期（5s 默认）
        int pollSec = config.getPollIntervalSec() == null ? 5 : config.getPollIntervalSec();
        if (lastPoll == null || !lastPoll.plusSeconds(pollSec).isAfter(LocalDateTime.now())) {
            lastPoll = LocalDateTime.now();
            pollAll();
        }
        maybeReport();
        maybeHeartbeat();
    }

    private void pollAll() {
        List<VirtualDevice> devices = config.getCloudRoomId() == null
                ? deviceRepository.findByStatusOrderById(0)
                : deviceRepository.findByStatusAndCloudRoomIdOrderById(0, config.getCloudRoomId());
        java.util.Set<String> activeCodes = new HashSet<>();
        for (VirtualDevice device : devices) {
            activeCodes.add(device.getDeviceCode());
            try {
                pollDevice(device);
                offlineStreak.remove(device.getDeviceCode());
            } catch (IOException ex) {
                int streak = offlineStreak.merge(device.getDeviceCode(), 1, Integer::sum);
                if (streak == 3) {
                    log.warn("设备离线（连续{}次轮询失败）deviceCode={}", streak, device.getDeviceCode());
                }
            }
        }
        // 删除/暂停设备的旧观测值不再参与周期上报（否则已删设备的样本会永久重发、
        // 平台侧每周期回报 4023 设备不存在）
        latestValues.keySet().retainAll(activeCodes);
    }

    private void pollDevice(VirtualDevice device) throws IOException {
        List<RegisterMapEntry> map = registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(device.getId());
        if (map.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> decoded = new HashMap<>();
        // 逐条目读取（寄存器地址可能不连续）；条目数量小，连接开销可接受
        for (RegisterMapEntry entry : map) {
            int[] regs = modbusMaster.readHoldingRegisters(LOCALHOST, device.getModbusPort(),
                    1, entry.getRegisterAddr(), 1);
            decoded.put(entry.getPointCode(), DataDecoder.decode(regs[0],
                    entry.getScaleFactor() == null ? 10 : entry.getScaleFactor().doubleValue()));
        }
        latestValues.put(device.getDeviceCode(), decoded);
    }

    private void maybeReport() {
        if (latestValues.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int reportSec = config.getReportIntervalSec() == null ? 60 : config.getReportIntervalSec();
        double deadband = config.getChangeDeadband() == null ? 0.5 : config.getChangeDeadband().doubleValue();

        List<NorthboundClient.SampleItem> items = new ArrayList<>();
        boolean periodicDue = lastPeriodicReportAt == null
                || lastPeriodicReportAt.plusSeconds(reportSec).isBefore(now);
        if (periodicDue) {
            lastPeriodicReportAt = now;
        }

        for (Map.Entry<String, Map<String, BigDecimal>> deviceEntry : latestValues.entrySet()) {
            String deviceCode = deviceEntry.getKey();
            List<String> points;
            if (periodicDue) {
                points = new ArrayList<>(deviceEntry.getValue().keySet());
            } else {
                // 周期内：变化死区 + 越限即报（越限受加密间隔节流）
                points = ReportStrategy.decide(deviceEntry.getValue(), lastReported,
                        LocalDateTime.now().minusSeconds(1), LocalDateTime.now(),
                        Integer.MAX_VALUE, deadband, thresholds);
            }
            for (String point : points) {
                BigDecimal value = deviceEntry.getValue().get(point);
                items.add(new NorthboundClient.SampleItem(deviceCode, point,
                        value.doubleValue(), now));
                lastReported.put(point, value);
            }
        }

        if (!items.isEmpty()) {
            boolean ok = true;
            for (int i = 0; i < items.size(); i += REPORT_BATCH_MAX) {
                if (!client.report(items.subList(i, Math.min(i + REPORT_BATCH_MAX, items.size())))) {
                    ok = false;
                }
            }
            if (ok) {
                log.debug("北向上报 room={} items={} periodic={}", config.getCloudRoomId(),
                        items.size(), periodicDue);
            }
        }
    }

    private void maybeHeartbeat() {
        // 心跳周期 30 秒，独立于上报节奏（无上报时也持续保活）
        LocalDateTime now = LocalDateTime.now();
        if (lastHeartbeatAt == null || lastHeartbeatAt.plusSeconds(30).isBefore(now)) {
            lastHeartbeatAt = now;
            client.heartbeat();
        }
    }

    /** 观测：本网关北向客户端统计（压测 qps/延迟） */
    NorthboundClient.Stats stats() {
        return client.stats();
    }

    /** 更新本网关的阈值缓存（按机房独立拉取，互不覆盖） */
    void updateThresholds(Map<String, List<ReportStrategy.Threshold>> thresholds) {
        this.thresholds = thresholds == null ? Map.of() : thresholds;
        log.info("网关阈值缓存已更新 room={} : {} 个指标", config.getCloudRoomId(), this.thresholds.size());
    }

    /** 观测：本网关最近一次轮询快照 */
    Map<String, Map<String, BigDecimal>> snapshot() {
        return Map.copyOf(latestValues);
    }

    /** 观测：本网关离线设备列表 */
    List<String> offlineDevices() {
        return offlineStreak.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .map(Map.Entry::getKey).toList();
    }

    void attachSchedulerFuture(ScheduledFuture<?> future) {
        this.scheduledFuture = future;
    }

    void cancelTick() {
        ScheduledFuture<?> future = scheduledFuture;
        if (future != null) {
            future.cancel(false);
        }
    }

    void shutdown() {
        client.close();
    }
}
