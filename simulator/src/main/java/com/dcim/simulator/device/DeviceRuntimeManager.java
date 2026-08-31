package com.dcim.simulator.device;

import com.dcim.simulator.device.EnvironmentChannel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.SplittableRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备模块运行时：管理全部虚拟设备的 slave server 生命周期、环境→寄存器采样循环与故障注入
 *
 * 因果链单向：环境纯函数求值 → 加噪 → 写寄存器；网关只能经 Modbus 读到。
 * SENSOR_STUCK=冻结寄存器写入；SENSOR_DEAD=slave 停止响应（master 超时判离线）。
 * 噪声为 (seed, pointCode, epochSec) 的纯函数——同种子同时刻结果一致，可测确定性。
 */
@Slf4j
@Component
public class DeviceRuntimeManager {

    private final VirtualDeviceRepository deviceRepository;
    private final RegisterMapEntryRepository registerMapRepository;
    private final RoomEnvironmentProfileRepository profileRepository;
    private final EnvironmentChannelRepository channelRepository;
    private final RegisterStore registerStore;
    private final RoomPhysicsEngine physicsEngine;

    private final Map<Long, ModbusSlaveServer> servers = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastSampleAt = new ConcurrentHashMap<>();

    @Value("${simulator.seed:42}")
    private long seed;

    public DeviceRuntimeManager(VirtualDeviceRepository deviceRepository,
                                RegisterMapEntryRepository registerMapRepository,
                                RoomEnvironmentProfileRepository profileRepository,
                                EnvironmentChannelRepository channelRepository,
                                RegisterStore registerStore,
                                RoomPhysicsEngine physicsEngine) {
        this.deviceRepository = deviceRepository;
        this.registerMapRepository = registerMapRepository;
        this.profileRepository = profileRepository;
        this.channelRepository = channelRepository;
        this.registerStore = registerStore;
        this.physicsEngine = physicsEngine;
    }

    /**
     * 启动恢复：扫库重建所有运行中设备的 slave server（不补宕机期间数据）
     */
    public void restoreAll() {
        for (VirtualDevice device : deviceRepository.findByStatusOrderById(0)) {
            try {
                startDevice(device);
            } catch (Exception ex) {
                log.error("设备恢复失败 deviceCode={}", device.getDeviceCode(), ex);
            }
        }
        log.info("设备模块恢复完成：运行中 slave 数={}", servers.size());
    }

    /**
     * 启动单台设备（建寄存器表 + 起 slave）
     */
    public void startDevice(VirtualDevice device) throws Exception {
        List<RegisterMapEntry> map = registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(device.getId());
        int maxAddr = map.stream().mapToInt(RegisterMapEntry::getRegisterAddr).max().orElse(-1);
        registerStore.initDevice(device.getId(), maxAddr + 1);
        ModbusSlaveServer server = new ModbusSlaveServer(device.getId(), device.getModbusPort(), registerStore);
        server.start();
        servers.put(device.getId(), server);
    }

    /**
     * 停止单台设备
     */
    public void stopDevice(Long deviceId) {
        ModbusSlaveServer server = servers.remove(deviceId);
        if (server != null) {
            server.stop();
        }
        registerStore.removeDevice(deviceId);
        lastSampleAt.remove(deviceId);
    }

    /**
     * 故障注入：NONE 恢复 / SENSOR_STUCK 冻结写入 / SENSOR_DEAD 停止 slave 响应
     */
    public void applyFault(Long deviceId, String faultMode) {
        ModbusSlaveServer server = servers.get(deviceId);
        switch (faultMode) {
            case "SENSOR_DEAD" -> {
                if (server != null) server.stop();
            }
            case "SENSOR_STUCK" -> log.info("寄存器写入冻结 deviceId={}", deviceId);
            case "NONE" -> {
                if (server != null && !server.isResponding()) {
                    try {
                        server.resume();
                    } catch (Exception ex) {
                        log.error("故障恢复失败 deviceId={}", deviceId, ex);
                    }
                }
            }
            default -> {
            }
        }
    }

    /**
     * 采样循环：每秒检查各设备是否到达采样周期，到达则刷新寄存器
     */
    @Scheduled(fixedDelay = 1000)
    public void sampleTick() {
        LocalDateTime now = LocalDateTime.now();
        for (VirtualDevice device : deviceRepository.findByStatusOrderById(0)) {
            if (!servers.containsKey(device.getId())) {
                continue;
            }
            int interval = device.getSampleIntervalSec() == null ? 1 : device.getSampleIntervalSec();
            LocalDateTime last = lastSampleAt.get(device.getId());
            if (last != null && last.plusSeconds(interval).isAfter(now)) {
                continue;
            }
            lastSampleAt.put(device.getId(), now);
            refreshDevice(device, now);
        }
    }

    /**
     * 刷新一台设备的全部寄存器（环境值+偏差+噪声 → raw）
     *
     * 物理接管指标（温湿度/送回风/功率）从机房室内状态派生——设备感知真实机房环境；
     * 其余指标仍走环境通道。设备间叠加确定性偏差（同型设备略有差异）。
     */
    void refreshDevice(VirtualDevice device, LocalDateTime now) {
        // SENSOR_STUCK：冻结寄存器（数据流恒值）
        if ("SENSOR_STUCK".equals(device.getFaultMode())) {
            return;
        }
        Long profileId = profileRepository.findByCloudRoomId(device.getCloudRoomId())
                .map(RoomEnvironmentProfile::getId).orElse(null);
        if (profileId == null) {
            return;
        }
        List<EnvironmentChannel> allChannels = channelRepository.findByProfileId(profileId);
        long epochSec = now.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
        for (RegisterMapEntry entry : registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(device.getId())) {
            BigDecimal value;
            java.util.OptionalDouble physics = physicsEngine.roomPoint(profileId, entry.getPointCode());
            if (physics.isPresent()) {
                value = BigDecimal.valueOf(applyDeviceBias(device, entry.getPointCode(), physics.getAsDouble()));
            } else {
                List<EnvironmentChannel> channels = allChannels.stream()
                        .filter(c -> entry.getPointCode().equals(c.getPointCode())).toList();
                if (channels.isEmpty()) {
                    continue;
                }
                value = EnvironmentEngine.evaluate(channels, now);
            }
            value = value.add(noise(device, entry.getPointCode(), epochSec));
            double scale = entry.getScaleFactor() == null ? 10 : entry.getScaleFactor().doubleValue();
            int raw = (int) Math.round(value.doubleValue() * scale);
            registerStore.write(device.getId(), entry.getRegisterAddr(), raw);
        }
    }

    /**
     * 设备确定性偏差：同型设备读机房环境时略有差异
     * （温度 ±0.4℃ / 湿度 ±2% 加性；功率 ±3% 乘性），(deviceCode, pointCode) 决定
     */
    double applyDeviceBias(VirtualDevice device, String pointCode, double base) {
        long h = (device.getDeviceCode() + ":" + pointCode).hashCode();
        double unit = new SplittableRandom(h).nextDouble(-1.0, 1.0);
        if ("power_kw".equals(pointCode)) {
            return base * (1 + unit * 0.03);
        }
        if (pointCode.endsWith("_temp")) {
            return base + unit * 0.4;
        }
        if ("humidity".equals(pointCode)) {
            return base + unit * 2.0;
        }
        return base;
    }

    /**
     * 确定性噪声：±amplitude 均匀分布，(seed, pointCode, epochSec) 纯函数
     */
    BigDecimal noise(VirtualDevice device, String pointCode, long epochSec) {
        double amplitude = device.getNoiseAmplitude() == null ? 0 : device.getNoiseAmplitude();
        if (amplitude <= 0) {
            return BigDecimal.ZERO;
        }
        long noiseSeed = seed ^ (device.getDeviceCode() + ":" + pointCode).hashCode() ^ epochSec;
        double unit = new SplittableRandom(noiseSeed).nextDouble(-1.0, 1.0);
        return BigDecimal.valueOf(unit * amplitude).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 运行态观测
     */
    public int runningServerCount() {
        return servers.size();
    }

    @PreDestroy
    public void shutdown() {
        servers.values().forEach(ModbusSlaveServer::stop);
    }
}
