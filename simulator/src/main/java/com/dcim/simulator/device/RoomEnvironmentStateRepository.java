package com.dcim.simulator.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 室内热湿状态数据访问接口
 */
@Repository
public interface RoomEnvironmentStateRepository extends JpaRepository<RoomEnvironmentState, Long> {
    Optional<RoomEnvironmentState> findByProfileId(Long profileId);
}
