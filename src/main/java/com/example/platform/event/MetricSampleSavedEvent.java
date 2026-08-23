package com.example.platform.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * 类功能:采样保存完成事件（T1事务提交后发布，仅向前传播至告警评估）
 * */
public record MetricSampleSavedEvent(
        Integer deviceId,
        Long pointId,
        BigDecimal sampleValue,
        LocalDateTime sampleTime) {
}
