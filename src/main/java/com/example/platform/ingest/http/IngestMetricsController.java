package com.example.platform.ingest.http;

import com.example.platform.common.Result;
import com.example.platform.ingest.metrics.IngestMetricsCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据接收门观测端点（阶段七压测配套，只读）
 *
 * <p>挂载于 /observability 前缀（/ingest/** 为网关机器鉴权专用，用户通道不可达），
 * 输出内存计数快照（批次/条目/延迟分位），供压测脚本轮询采集；
 * 数据为进程内存态，重启归零，快照自带 since 时间戳供调用方差分。</p>
 */
@Tag(name = "数据接收门（观测）", description = "ingest 链路吞吐与延迟观测（压测采样用，只读）")
@RestController
@RequestMapping("/observability/ingest-metrics")
public class IngestMetricsController {

    private final IngestMetricsCollector metricsCollector;

    public IngestMetricsController(IngestMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Operation(summary = "查询 ingest 观测快照",
            description = "批次计数（分 HTTP/MQTT）、条目计数（接受/去重/失败/自动注册）、"
                    + "批处理耗时直方图与 P50/P95/P99 估算；自进程启动累计，重启归零")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Map<String, Object>> snapshot() {
        return Result.success(metricsCollector.snapshot());
    }
}
