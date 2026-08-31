package com.dcim.simulator.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RoomEnvironmentProfile 数据访问接口
 */
@Repository
public interface RoomEnvironmentProfileRepository extends JpaRepository<RoomEnvironmentProfile, Long> {
    Optional<RoomEnvironmentProfile> findByCloudRoomId(Long cloudRoomId);
}
