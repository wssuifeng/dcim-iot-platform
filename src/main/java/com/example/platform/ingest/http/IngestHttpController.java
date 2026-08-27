package com.example.platform.ingest.http;

import com.example.platform.common.Result;
import com.example.platform.ingest.dto.IngestInventoryDTO;
import com.example.platform.ingest.dto.IngestInventoryResultVO;
import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据接收门 HTTP 适配器（网关北向上报入口，机器鉴权，非用户 JWT 通道）
 *
 * <p>鉴权由 {@link GatewayAuthenticationFilter} 完成（Authorization: Gateway {accessKey}:{signature}，
 * 签名 = sha256(secret + rawBody)）。本控制器只做传输层接入，业务语义全部在
 * IngestCoreService——MQTT 适配器收到相同契约的数据走同一条处理链。</p>
 */
@Tag(name = "数据接收门（网关上报）", description = "网关北向数据上报与心跳（机器鉴权，HTTP 通道）")
@RestController
@RequestMapping("/ingest")
public class IngestHttpController {

    private final IngestCoreService ingestCoreService;
    private final IngestInventoryService ingestInventoryService;

    public IngestHttpController(IngestCoreService ingestCoreService,
                                IngestInventoryService ingestInventoryService) {
        this.ingestCoreService = ingestCoreService;
        this.ingestInventoryService = ingestInventoryService;
    }

    @Operation(summary = "网关批量数据上报",
            description = "单批 ≤ 200 条；逐条独立事务部分成功合法；deviceCode+pointCode+seq 重复计入 duplicated"
                    + "（QoS1 重发常态）；sampleTime 早于平台时间为补传合法，晚于+5分钟该条失败；"
                    + "设备不属于凭证绑定机房时该条失败（越权不落库）")
    @PostMapping("/samples")
    public Result<IngestResultVO> samples(@Valid @RequestBody IngestSampleBatchDTO batch,
                                          HttpServletRequest request) {
        IngestCredential credential =
                (IngestCredential) request.getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL);
        return Result.success(ingestCoreService.processSamples(credential, batch));
    }

    @Operation(summary = "网关心跳保活",
            description = "60s 周期调用，刷新凭证 last_seen_at 作为网关存活观测依据；"
                    + "MQTT 通道等价物为周期心跳消息 + 连接遗嘱（LWT）")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(HttpServletRequest request) {
        IngestCredential credential =
                (IngestCredential) request.getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL);
        ingestCoreService.heartbeat(credential);
        return Result.success();
    }

    @Operation(summary = "网关设备清单上报",
            description = "清单上报式接入：auto_register=1 时逐台导入（已存在跳过并补建缺失点位绑定），"
                    + "否则仅返回 unknown 比对结果供前端人工导入；单批 ≤ 500 台")
    @PostMapping("/inventory")
    public Result<IngestInventoryResultVO> inventory(@Valid @RequestBody IngestInventoryDTO inventory,
                                                     HttpServletRequest request) {
        IngestCredential credential =
                (IngestCredential) request.getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL);
        return Result.success(ingestInventoryService.handleInventory(credential, inventory));
    }
}
