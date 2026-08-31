package com.dcim.simulator.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EnvironmentChannel 数据访问接口
 */
@Repository
public interface EnvironmentChannelRepository extends JpaRepository<EnvironmentChannel, Long> {
    List<EnvironmentChannel> findByProfileIdAndPointCode(Long profileId, String pointCode);
    List<EnvironmentChannel> findByProfileId(Long profileId);
    @Transactional
    void deleteByProfileIdAndPointCode(Long profileId, String pointCode);
}
