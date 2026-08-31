package com.dcim.simulator.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上报策略：PERIODIC（到周期报全部）/ ON_CHANGE（死区）/ ON_ALARM（越限加密上报）
 *
 * 判定纯函数化（输入当前值/上次上报/时间/配置），便于确定性测试；
 * 越限判定用平台规则缓存的阈值（operator GT/LT + threshold），无阈值时退化为 ON_CHANGE。
 */
public final class ReportStrategy {

    /** 平台告警规则缓存条目 */
    public record Threshold(String operator, BigDecimal threshold) {
    }

    /**
     * 计算本轮应上报的指标
     */
    public static List<String> decide(Map<String, BigDecimal> currentValues,
                                      Map<String, BigDecimal> lastReported,
                                      LocalDateTime lastPeriodicReportAt,
                                      LocalDateTime now,
                                      int reportIntervalSec,
                                      double changeDeadband,
                                      Map<String, List<Threshold>> thresholds) {
        // 周期到点：全部上报
        if (lastPeriodicReportAt == null
                || lastPeriodicReportAt.plusSeconds(reportIntervalSec).isBefore(now)) {
            return new ArrayList<>(currentValues.keySet());
        }
        List<String> toReport = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : currentValues.entrySet()) {
            String point = entry.getKey();
            BigDecimal value = entry.getValue();
            // 越限即报（不受周期约束，受网关侧最小加密间隔由调用方节流）
            if (violates(value, thresholds.get(point))) {
                toReport.add(point);
                continue;
            }
            // 变化死区
            BigDecimal last = lastReported.get(point);
            if (last == null || last.subtract(value).abs().doubleValue() > changeDeadband) {
                toReport.add(point);
            }
        }
        return toReport;
    }

    /**
     * 值是否违反任一阈值（GT 大于触发 / LT 小于触发）
     */
    public static boolean violates(BigDecimal value, List<Threshold> thresholds) {
        if (thresholds == null) {
            return false;
        }
        for (Threshold t : thresholds) {
            if ("GT".equals(t.operator()) && value.compareTo(t.threshold()) > 0) {
                return true;
            }
            if ("LT".equals(t.operator()) && value.compareTo(t.threshold()) < 0) {
                return true;
            }
        }
        return false;
    }

    private ReportStrategy() {
    }
}
