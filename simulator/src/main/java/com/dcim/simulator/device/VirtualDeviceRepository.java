package com.dcim.simulator.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * VirtualDevice 数据访问接口
 */
@Repository
public interface VirtualDeviceRepository extends JpaRepository<VirtualDevice, Long> {
    Optional<VirtualDevice> findByDeviceCode(String deviceCode);
    Optional<VirtualDevice> findByModbusPort(Integer modbusPort);
    List<VirtualDevice> findByStatusOrderById(Integer status);
    List<VirtualDevice> findByStatusAndCloudRoomIdOrderById(Integer status, Long cloudRoomId);
}
