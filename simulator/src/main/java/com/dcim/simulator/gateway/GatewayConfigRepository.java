package com.dcim.simulator.gateway;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * GatewayConfig 数据访问接口
 */
@Repository
public interface GatewayConfigRepository extends JpaRepository<GatewayConfig, Long> {
}
