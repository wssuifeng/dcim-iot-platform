package com.dcim.simulator.lifecycle;

import com.dcim.simulator.device.DeviceRuntimeManager;
import com.dcim.simulator.device.RegisterMapEntry;
import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.VirtualDevice;
import com.dcim.simulator.device.VirtualDeviceRepository;
import com.dcim.simulator.gateway.GatewayConfig;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.gateway.GatewayRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仿真器生命周期：启动恢复（扫库重建设备 slave 与网关运行时），kill 后重启自动恢复
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorLifecycleService implements ApplicationRunner {

    private final VirtualDeviceRepository deviceRepository;
    private final RegisterMapEntryRepository registerMapRepository;
    private final DeviceRuntimeManager deviceRuntimeManager;
    private final GatewayConfigRepository gatewayConfigRepository;
    private final GatewayRuntime gatewayRuntime;

    @Value("${simulator.modbus-port-base:15020}")
    private int modbusPortBase;

    @Override
    public void run(ApplicationArguments args) {
        deviceRuntimeManager.restoreAll();
        backfillGatewayRoomBinding();
        gatewayConfigRepository.findAll().stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 0)
                .forEach(config -> {
                    try {
                        gatewayRuntime.start(config);
                    } catch (Exception ex) {
                        log.error("网关实例启动失败 configId={}", config.getId(), ex);
                    }
                });
        log.info("dcim-simulator 启动恢复完成");
    }

    /**
     * 旧单网关数据回填：cloud_room_id 为空时按设备归属推断
     * （全部设备同机房→该机房；跨机房或无设备→ -1 占位，管理页重新绑定）
     */
    void backfillGatewayRoomBinding() {
        List<Long> deviceRooms = deviceRepository.findAll().stream()
                .map(VirtualDevice::getCloudRoomId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        Long inferred = deviceRooms.size() == 1 ? deviceRooms.get(0) : -1L;
        for (GatewayConfig config : gatewayConfigRepository.findAll()) {
            if (config.getCloudRoomId() == null) {
                config.setCloudRoomId(inferred);
                gatewayConfigRepository.save(config);
                log.info("旧网关配置回填机房绑定 configId={} cloudRoomId={}", config.getId(), inferred);
            }
        }
    }

    /**
     * 新建虚拟设备时分配最小可用端口
     */
    public int nextAvailablePort() {
        return deviceRepository.findAll().stream()
                .mapToInt(VirtualDevice::getModbusPort)
                .max().orElse(modbusPortBase - 1) + 1;
    }

    /**
     * 设备变更后同步运行时（slave 重启 + 故障模式应用）
     */
    public void restartDevice(VirtualDevice device) {
        deviceRuntimeManager.stopDevice(device.getId());
        try {
            deviceRuntimeManager.startDevice(device);
            deviceRuntimeManager.applyFault(device.getId(), device.getFaultMode());
        } catch (Exception ex) {
            log.error("设备运行时重启失败 deviceCode={}", device.getDeviceCode(), ex);
        }
    }

    /**
     * 网关配置变更后热重载
     */
    public void reloadGateway(GatewayConfig config) {
        gatewayRuntime.start(config);
    }
}
