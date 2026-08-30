package com.example.platform.ingest.http;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.Result;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.PointDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网关侧配置导出接口（阈值/机房目录）——Gateway 机器鉴权，网关对云平台管理面的唯一只读窗口
 *
 * <p>松耦合设计：网关不依赖云平台实现本地告警（用户决策），但可从这里拉取阈值目录做
 * ON_ALARM 判定的初始配置；机房清单顺带返回，供网关侧管理页展示（免管理账号）。</p>
 *
 * <p>阈值口径：按凭证所属机房内全部设备的<b>生效模版</b>（设备改绑优先→类型基础模版）
 * 聚合去重后导出启用中的 METRIC 规则行。</p>
 */
@Tag(name = "网关配置导出", description = "阈值与机房目录导出（网关 Gateway 签名鉴权）")
@RestController
@RequestMapping("/ingest")
public class IngestExportController {

    private final AlarmTemplateItemMapper templateItemMapper;
    private final AlarmTemplateService alarmTemplateService;
    private final DeviceMapper deviceMapper;
    private final PointDefinitionService pointDefinitionService;
    private final IngestCredentialService ingestCredentialService;

    public IngestExportController(AlarmTemplateItemMapper templateItemMapper,
                                  AlarmTemplateService alarmTemplateService,
                                  DeviceMapper deviceMapper,
                                  PointDefinitionService pointDefinitionService,
                                  IngestCredentialService ingestCredentialService) {
        this.templateItemMapper = templateItemMapper;
        this.alarmTemplateService = alarmTemplateService;
        this.deviceMapper = deviceMapper;
        this.pointDefinitionService = pointDefinitionService;
        this.ingestCredentialService = ingestCredentialService;
    }

    @Operation(summary = "导出告警阈值与机房目录",
            description = "网关本地告警判定的配置源：按机房内全部设备生效模版聚合 METRIC 规则为 "
                    + "[{pointCode, operator, threshold, alarmLevel}]；机房目录顺带返回。"
                    + "Authorization: Gateway {accessKey}:{signature}（空体签名）")
    @GetMapping("/export/thresholds")
    public Result<Map<String, Object>> exportThresholds(HttpServletRequest request) {
        IngestCredential credential =
                (IngestCredential) request.getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL);

        // 指标 id → code 映射
        Map<Long, String> pointCodeById = pointDefinitionService.list().stream()
                .collect(Collectors.toMap(PointDefinition::getId, PointDefinition::getPointCode));

        // 机房内全部设备的生效模版去重
        IngestCredential full = ingestCredentialService.getById(credential.getId());
        LambdaQueryWrapper<Device> deviceWrapper = new LambdaQueryWrapper<>();
        deviceWrapper.eq(Device::getCloudRoomId, full.getCloudRoomId());
        Set<Long> templateIds = new LinkedHashSet<>();
        for (Device device : deviceMapper.selectList(deviceWrapper)) {
            AlarmTemplate template = alarmTemplateService.resolveEffectiveTemplate(device);
            if (template != null) {
                templateIds.add(template.getId());
            }
        }

        // 生效模版下启用的 METRIC 规则按指标聚合导出
        List<ThresholdItem> thresholds = new ArrayList<>();
        if (!templateIds.isEmpty()) {
            LambdaQueryWrapper<AlarmTemplateItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.in(AlarmTemplateItem::getTemplateId, templateIds)
                    .eq(AlarmTemplateItem::getRuleType, "METRIC")
                    .eq(AlarmTemplateItem::getEnabled, 1)
                    .isNotNull(AlarmTemplateItem::getPointId);
            for (AlarmTemplateItem item : templateItemMapper.selectList(itemWrapper)) {
                String pointCode = pointCodeById.get(item.getPointId());
                if (pointCode == null) {
                    continue;
                }
                thresholds.add(new ThresholdItem(pointCode, item.getOperator(),
                        item.getThreshold(), item.getAlarmLevel(),
                        item.getBreachCount(), item.getRateLimit(),
                        item.getRateWindowSec(), item.getReAlarmSec(), item.getHysteresis()));
            }
        }

        // 机房目录（鉴权凭证所属机房）——网关侧管理页展示用
        List<Map<String, Object>> rooms = List.of(Map.of(
                "roomId", full.getCloudRoomId(),
                "accessKey", full.getAccessKey()));

        return Result.success(Map.of(
                "thresholds", thresholds,
                "rooms", rooms));
    }

    /**
     * 阈值条目（S8-1 起携带机制字段透出：网关侧仅透传展示，判定仍在平台）
     */
    public record ThresholdItem(String pointCode, String operator,
                                java.math.BigDecimal threshold, Integer alarmLevel,
                                Integer breachCount, java.math.BigDecimal rateLimit,
                                Integer rateWindowSec, Integer reAlarmSec,
                                java.math.BigDecimal hysteresis) {
    }
}
