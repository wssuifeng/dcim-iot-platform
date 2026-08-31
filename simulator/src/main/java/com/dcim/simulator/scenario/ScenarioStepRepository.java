package com.dcim.simulator.scenario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ScenarioStep 数据访问接口
 */
@Repository
public interface ScenarioStepRepository extends JpaRepository<ScenarioStep, Long> {
    List<ScenarioStep> findByScenarioIdOrderByStepOrder(Long scenarioId);
}
