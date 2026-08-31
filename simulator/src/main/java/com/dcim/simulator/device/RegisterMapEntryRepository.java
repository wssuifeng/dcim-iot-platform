package com.dcim.simulator.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RegisterMapEntry 数据访问接口
 */
@Repository
public interface RegisterMapEntryRepository extends JpaRepository<RegisterMapEntry, Long> {
    List<RegisterMapEntry> findByDeviceIdOrderByRegisterAddrAsc(Long deviceId);
}
