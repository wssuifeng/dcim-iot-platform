package com.dcim.simulator.gateway;

import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.VirtualDeviceRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 网关运行时注册表：多网关实例的创建、启停、热重载与按秒调度
 *
 * 说明:一机房一网关实例（阶段七多实例化），每实例独立单线程调度互不阻塞；
 *   聚合观测方法（snapshot/offlineDevices）合并全部运行实例，兼容旧单网关视图；
 *   网关数上限 16（池大小即上限），超出拒绝启动。
 */
@Slf4j
@Component
public class GatewayRuntime {

    /** 网关实例数上限（调度池大小） */
    public static final int MAX_GATEWAYS = 16;

    private final VirtualDeviceRepository deviceRepository;
    private final RegisterMapEntryRepository registerMapRepository;
    private final ModbusMaster modbusMaster;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(MAX_GATEWAYS);
    /** configId → 运行实例 */
    private final Map<Long, GatewayInstance> running = new ConcurrentHashMap<>();

    public GatewayRuntime(VirtualDeviceRepository deviceRepository,
                          RegisterMapEntryRepository registerMapRepository,
                          ModbusMaster modbusMaster) {
        this.deviceRepository = deviceRepository;
        this.registerMapRepository = registerMapRepository;
        this.modbusMaster = modbusMaster;
    }

    /**
     * 启动/重启网关实例（配置变更后调用；同 id 旧实例先停止）
     */
    public synchronized void start(GatewayConfig config) {
        if (config.getId() == null) {
            throw new IllegalArgumentException("网关配置必须先保存（id 不能为空）");
        }
        if (!running.containsKey(config.getId()) && running.size() >= MAX_GATEWAYS) {
            throw new IllegalStateException("网关实例数已达上限 " + MAX_GATEWAYS);
        }
        stop(config.getId());
        GatewayInstance instance = new GatewayInstance(config, deviceRepository,
                registerMapRepository, modbusMaster);
        running.put(config.getId(), instance);
        scheduleTick(instance);
        log.info("网关运行时启动 name={} room={} transport={}", config.getName(),
                config.getCloudRoomId(), config.getNorthboundTransport());
    }

    private void scheduleTick(GatewayInstance instance) {
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                instance::tick, 1, 1, TimeUnit.SECONDS);
        instance.attachSchedulerFuture(future);
    }

    /**
     * 停止网关实例（未运行则无操作）
     */
    public synchronized void stop(Long configId) {
        GatewayInstance old = running.remove(configId);
        if (old != null) {
            old.cancelTick();
            old.shutdown();
            log.info("网关实例停止 configId={}", configId);
        }
    }

    /**
     * 观测：指定网关运行实例（未运行返回 null）
     */
    public GatewayInstance instanceOf(Long configId) {
        return running.get(configId);
    }

    /**
     * 观测：网关是否运行中
     */
    public boolean isRunning(Long configId) {
        return running.containsKey(configId);
    }

    /**
     * 观测：运行中网关 ID 集合
     */
    public List<Long> runningIds() {
        return new ArrayList<>(running.keySet());
    }

    /**
     * 观测（兼容聚合视图）：全部运行实例的最近轮询快照合并
     * （deviceCode 全局唯一，按机房分实例存储后合并无歧义）
     */
    public Map<String, Map<String, BigDecimal>> snapshot() {
        Map<String, Map<String, BigDecimal>> merged = new LinkedHashMap<>();
        for (GatewayInstance instance : running.values()) {
            merged.putAll(instance.snapshot());
        }
        return merged;
    }

    /**
     * 观测（兼容聚合视图）：全部运行实例的离线设备合并
     */
    public List<String> offlineDevices() {
        List<String> merged = new ArrayList<>();
        for (GatewayInstance instance : running.values()) {
            merged.addAll(instance.offlineDevices());
        }
        return merged;
    }

    /**
     * 观测：全部网关的负载统计（id → 运行中客户端统计，未运行不出现）
     */
    public Map<Long, NorthboundClient.Stats> loadStats() {
        Map<Long, NorthboundClient.Stats> stats = new java.util.HashMap<>();
        running.forEach((id, instance) -> stats.put(id, instance.stats()));
        return stats;
    }

    /**
     * 更新指定网关的阈值缓存（按机房独立拉取）
     */
    public void updateThresholds(Long configId, Map<String, List<ReportStrategy.Threshold>> thresholds) {
        GatewayInstance instance = running.get(configId);
        if (instance != null) {
            instance.updateThresholds(thresholds);
        } else {
            log.warn("阈值缓存更新目标网关未运行 configId={}", configId);
        }
    }

    /**
     * 兼容旧调用（单网关语义）：作用于第一个运行实例
     */
    public void updateThresholds(Map<String, List<ReportStrategy.Threshold>> thresholds) {
        GatewayInstance first = running.values().stream().findFirst().orElse(null);
        if (first != null) {
            first.updateThresholds(thresholds);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Long configId : running.keySet()) {
            stop(configId);
        }
        scheduler.shutdownNow();
    }
}
