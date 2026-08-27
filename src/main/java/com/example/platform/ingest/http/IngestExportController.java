package com.example.platform.ingest.http;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.Result;
import com.example.platform.entity.DevicePointAlarmRule;
import com.example.platform.entity.PointDefinition;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.service.PointDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关侧配置导出接口（阈值/机房目录）——Gateway 机器鉴权，网关对云平台管理面的唯一只读窗口
 *
 * <p>松耦合设计：网关不依赖云平台实现本地告警（用户决策），但可从这里拉取阈值目录做
 * ON_ALARM 判定的初始配置；机房清单顺带返回，供网关侧管理页展示（免管理账号）。</p>
 */
@Tag(name = "网关配置导出", description = "阈值与机房目录导出（网关 Gateway 签名鉴权）")
@RestController
@RequestMapping("/ingest")
public class IngestExportController {

    private final DevicePointAlarmRuleMapper alarmRuleMapper;
    private final PointDefinitionService pointDefinitionService;
    private final IngestCredentialService ingestCredentialService;

    public IngestExportController(DevicePointAlarmRuleMapper alarmRuleMapper,
                                  PointDefinitionService pointDefinitionService,
                                  IngestCredentialService ingestCredentialService) {
        this.alarmRuleMapper = alarmRuleMapper;
        this.pointDefinitionService = pointDefinitionService;
        this.ingestCredentialService = ingestCredentialService;
    }

    @Operation(summary = "导出告警阈值与机房目录",
            description = "网关本地告警判定的配置源：METRIC 规则按点位聚合为 "
                    + "[{pointCode, operator, threshold, alarmLevel}]；机房目录顺带返回。"
                    + "Authorization: Gateway {accessKey}:{signature}（空体签名）")
    @GetMapping("/export/thresholds")
    public Result<Map<String, Object>> exportThresholds(HttpServletRequest request) {
        IngestCredential credential =
                (IngestCredential) request.getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL);

        // 点位 id → code 映射
        Map<Long, String> pointCodeById = pointDefinitionService.list().stream()
                .collect(Collectors.toMap(PointDefinition::getId, PointDefinition::getPointCode));

        // 启用的 METRIC 规则按点位聚合
        List<DevicePointAlarmRule> rules = alarmRuleMapper.selectList(
                new LambdaQueryWrapper<DevicePointAlarmRule>()
                        .eq(DevicePointAlarmRule::getRuleType, "METRIC")
                        .eq(DevicePointAlarmRule::getEnabled, 1)
                        .isNotNull(DevicePointAlarmRule::getPointId));
        List<ThresholdItem> thresholds = new ArrayList<>();
        for (DevicePointAlarmRule rule : rules) {
            String pointCode = pointCodeById.get(rule.getPointId());
            if (pointCode == null) {
                continue;
            }
            thresholds.add(new ThresholdItem(pointCode, rule.getOperator(),
                    rule.getThreshold(), rule.getAlarmLevel()));
        }

        // 机房目录（鉴权凭证所属机房）——网关侧管理页展示用
        IngestCredential full = ingestCredentialService.getById(credential.getId());
        List<Map<String, Object>> rooms = List.of(Map.of(
                "roomId", full.getCloudRoomId(),
                "accessKey", full.getAccessKey()));

        return Result.success(Map.of(
                "thresholds", thresholds,
                "rooms", rooms));
    }

    /**
     * 阈值条目
     */
    public record ThresholdItem(String pointCode, String operator,
                                java.math.BigDecimal threshold, Integer alarmLevel) {
    }
}
