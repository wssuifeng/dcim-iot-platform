package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.PointAlarmRuleCreateDTO;
import com.example.platform.dto.PointAlarmRuleUpdateDTO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointAlarmRule;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
import com.example.platform.service.DevicePointAlarmRuleService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * 类功能:点位告警规则服务实现类，规则CRUD与降级解析
 *
 * 【两级降级（按点位替换式）】
 *   1. 解析设备生效模板
 *   2. 收集规则：生效模板项上的模板级规则（device_id IS NULL）+ 该设备的覆盖规则（device_id=设备ID）
 *   3. 按 pointId 分组替换：组内存在设备级规则→整组取设备级；否则取模板级
 *   4. source 标记：DEVICE_OVERRIDE / TEMPLATE
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePointAlarmRuleServiceImpl
        extends ServiceImpl<DevicePointAlarmRuleMapper, DevicePointAlarmRule>
        implements DevicePointAlarmRuleService {

    private static final int ENABLED_ON = 1;
    private static final int ENABLED_OFF = 0;
    private static final BigDecimal OFFLINE_MIN_MINUTES = BigDecimal.ONE;
    private static final BigDecimal OFFLINE_MAX_MINUTES = new BigDecimal("1440");

    private final DeviceTypePointTemplateMapper templateMapper;
    private final DeviceService deviceService;
    private final DeviceScopeHelper deviceScopeHelper;
    private final PointDefinitionService pointDefinitionService;

    @Override
    public Page<PointAlarmRuleVO> pageRules(Long currentUserId, Integer current, Integer size,
                                            Integer typeId, Integer deviceId, Long pointId,
                                            String ruleType, Integer enabled) {
        int[] params = PaginationUtils.validate(current, size);

        LambdaQueryWrapper<DevicePointAlarmRule> wrapper = new LambdaQueryWrapper<>();
        if (typeId != null) {
            List<Long> itemIds = listTemplateItemIdsByType(typeId);
            if (itemIds.isEmpty()) {
                Page<PointAlarmRuleVO> empty = new Page<>(params[0], params[1]);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
            wrapper.in(DevicePointAlarmRule::getTemplateItemId, itemIds);
        }
        if (deviceId != null) {
            deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
            wrapper.and(w -> w.eq(DevicePointAlarmRule::getDeviceId, deviceId)
                    .or().isNull(DevicePointAlarmRule::getDeviceId));
        }
        if (pointId != null) {
            wrapper.eq(DevicePointAlarmRule::getPointId, pointId);
        }
        if (StringUtils.hasText(ruleType)) {
            wrapper.eq(DevicePointAlarmRule::getRuleType, ruleType.trim().toUpperCase());
        }
        if (enabled != null) {
            wrapper.eq(DevicePointAlarmRule::getEnabled, enabled);
        }
        wrapper.orderByDesc(DevicePointAlarmRule::getUpdateTime);

        Page<DevicePointAlarmRule> source = page(new Page<>(params[0], params[1]), wrapper);
        Page<PointAlarmRuleVO> result = new Page<>(params[0], params[1]);
        result.setTotal(source.getTotal());
        List<PointAlarmRuleVO> records = source.getRecords().stream()
                .map(PointAlarmRuleVO::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
        fillPointFields(records);
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointAlarmRuleVO create(PointAlarmRuleCreateDTO dto, Long currentUserId) {
        DeviceTypePointTemplate item = templateMapper.selectById(dto.getTemplateItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.ALARM_RULE_INVALID, "模板指标项不存在: " + dto.getTemplateItemId());
        }
        validateRuleOwnership(item, dto.getDeviceId(), currentUserId);
        DevicePointAlarmRule rule = buildRule(dto, item);
        validateRuleShape(rule);
        validateRuleDuplicate(rule, null);
        if (!save(rule)) {
            throw new BusinessException("创建告警规则失败");
        }
        log.info("告警规则创建 ruleId={} templateItemId={} type={} device={}",
                rule.getId(), rule.getTemplateItemId(), rule.getRuleType(), rule.getDeviceId());
        PointAlarmRuleVO vo = PointAlarmRuleVO.fromEntity(rule);
        fillPointFields(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointAlarmRuleVO update(Long id, PointAlarmRuleUpdateDTO dto, Long currentUserId) {
        DevicePointAlarmRule rule = getRuleOrThrow(id);
        ensureRuleAccess(rule, currentUserId);
        if (StringUtils.hasText(dto.getRuleName())) {
            rule.setRuleName(dto.getRuleName());
        }
        rule.setOperator(dto.getOperator());
        rule.setThreshold(dto.getThreshold());
        if (dto.getAlarmLevel() != null) {
            rule.setAlarmLevel(dto.getAlarmLevel());
        }
        if (dto.getEnabled() != null) {
            rule.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? ENABLED_ON : ENABLED_OFF);
        }
        if (dto.getRemark() != null) {
            rule.setRemark(dto.getRemark());
        }
        validateRuleShape(rule);
        validateRuleDuplicate(rule, id);
        if (!updateById(rule)) {
            throw new BusinessException("更新告警规则失败");
        }
        PointAlarmRuleVO vo = PointAlarmRuleVO.fromEntity(rule);
        fillPointFields(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        DevicePointAlarmRule rule = getRuleOrThrow(id);
        ensureRuleAccess(rule, currentUserId);
        removeById(id);
        log.info("告警规则删除 ruleId={} operator={}", id, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, boolean enabled, Long currentUserId) {
        DevicePointAlarmRule rule = getRuleOrThrow(id);
        ensureRuleAccess(rule, currentUserId);
        rule.setEnabled(enabled ? ENABLED_ON : ENABLED_OFF);
        if (!updateById(rule)) {
            throw new BusinessException("更新告警规则状态失败");
        }
    }

    @Override
    public List<PointAlarmRuleVO> findEffectiveRules(Integer deviceId, Long pointId, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "查询生效规则必须提供当前用户");
        }
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        return findEffectiveRules(deviceId, pointId);
    }

    @Override
    public List<PointAlarmRuleVO> findEffectiveRules(Integer deviceId, Long pointId) {
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        // 1. 解析生效模板
        List<DeviceTypePointTemplate> effectiveItems = resolveEffectiveTemplateItems(device.getTypeId());
        if (effectiveItems.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> itemIds = effectiveItems.stream()
                .map(DeviceTypePointTemplate::getId)
                .collect(Collectors.toSet());

        // 2. 收集模板级规则与设备覆盖规则（仅启用）
        LambdaQueryWrapper<DevicePointAlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DevicePointAlarmRule::getEnabled, ENABLED_ON)
                .and(w -> w.isNull(DevicePointAlarmRule::getDeviceId)
                        .or().eq(DevicePointAlarmRule::getDeviceId, deviceId))
                .in(DevicePointAlarmRule::getTemplateItemId, itemIds);
        if (pointId != null) {
            wrapper.eq(DevicePointAlarmRule::getPointId, pointId);
        }
        List<DevicePointAlarmRule> rules = list(wrapper);
        if (rules.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 按点位分组替换：存在设备级规则→该点位组整体取设备级；否则取模板级
        Map<Long, List<DevicePointAlarmRule>> templateRules = new HashMap<>();
        Map<Long, List<DevicePointAlarmRule>> overrideRules = new HashMap<>();
        for (DevicePointAlarmRule rule : rules) {
            Long key = rule.getPointId() != null ? rule.getPointId() : Long.MIN_VALUE;
            if (rule.getDeviceId() != null) {
                overrideRules.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            } else {
                templateRules.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        }
        Map<Long, DeviceTypePointTemplate> itemById = effectiveItems.stream()
                .collect(Collectors.toMap(DeviceTypePointTemplate::getId, Function.identity()));

        Set<Long> seenPoints = new HashSet<>();
        seenPoints.addAll(templateRules.keySet());
        seenPoints.addAll(overrideRules.keySet());

        List<PointAlarmRuleVO> result = new ArrayList<>();
        for (Long key : seenPoints) {
            List<DevicePointAlarmRule> chosen;
            String source;
            if (overrideRules.containsKey(key)) {
                chosen = overrideRules.get(key);
                source = PointAlarmRuleVO.SOURCE_DEVICE_OVERRIDE;
            } else {
                chosen = templateRules.get(key);
                source = resolveTemplateSource(chosen.get(0), itemById);
            }
            for (DevicePointAlarmRule rule : chosen) {
                PointAlarmRuleVO vo = PointAlarmRuleVO.fromEntity(rule);
                vo.setSource(source);
                result.add(vo);
            }
        }
        // 评估顺序：按点位分组内级别升序（最严重在前），跨点位按点位ID稳定排序
        result.sort(Comparator
                .comparing(PointAlarmRuleVO::getPointId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PointAlarmRuleVO::getAlarmLevel));
        fillPointFields(result);
        return result;
    }

    // ===================== 内部辅助 =====================

    private List<DeviceTypePointTemplate> resolveEffectiveTemplateItems(Integer typeId) {
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId);
        return templateMapper.selectList(wrapper);
    }

    private String resolveTemplateSource(DevicePointAlarmRule rule,
                                         Map<Long, DeviceTypePointTemplate> itemById) {
        DeviceTypePointTemplate item = itemById.get(rule.getTemplateItemId());
        if (item == null) {
            return PointAlarmRuleVO.SOURCE_TEMPLATE;
        }
        return PointAlarmRuleVO.SOURCE_TEMPLATE;
    }

    private DevicePointAlarmRule getRuleOrThrow(Long id) {
        DevicePointAlarmRule rule = getById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.ALARM_RULE_NOT_FOUND, "告警规则不存在: " + id);
        }
        return rule;
    }

    private void ensureRuleAccess(DevicePointAlarmRule rule, Long currentUserId) {
        if (rule.getDeviceId() != null) {
            deviceScopeHelper.ensureDeviceAccess(rule.getDeviceId(), currentUserId);
        }
    }

    private void validateRuleOwnership(DeviceTypePointTemplate item, Integer deviceId, Long currentUserId) {
        if (deviceId != null) {
            deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        }
    }

    private DevicePointAlarmRule buildRule(PointAlarmRuleCreateDTO dto, DeviceTypePointTemplate item) {
        DevicePointAlarmRule rule = new DevicePointAlarmRule();
        rule.setTemplateItemId(item.getId());
        rule.setDeviceId(dto.getDeviceId());
        rule.setRuleType(dto.getRuleType().trim().toUpperCase());
        rule.setRuleName(dto.getRuleName().trim());
        rule.setOperator(StringUtils.hasText(dto.getOperator()) ? dto.getOperator().trim().toUpperCase() : null);
        rule.setThreshold(dto.getThreshold());
        rule.setAlarmLevel(dto.getAlarmLevel());
        rule.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? ENABLED_ON : ENABLED_OFF);
        rule.setRemark(dto.getRemark());
        // METRIC 规则点位一律取模板指标项点位；通用规则（OFFLINE/COMM）点位为空
        rule.setPointId("METRIC".equals(rule.getRuleType()) ? item.getPointId() : null);
        if (dto.getDeviceId() != null) {
            Device device = deviceService.getById(dto.getDeviceId());
            if (device == null) {
                throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + dto.getDeviceId());
            }
            if (!Objects.equals(device.getTypeId(), item.getTypeId())) {
                throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                        "设备覆盖规则的设备必须属于模板对应设备类型");
            }
        }
        return rule;
    }

    private void validateRuleShape(DevicePointAlarmRule rule) {
        switch (rule.getRuleType()) {
            case "METRIC" -> {
                if (!StringUtils.hasText(rule.getOperator()) || rule.getThreshold() == null) {
                    throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                            "METRIC 规则必须提供比较符与阈值");
                }
            }
            case "OFFLINE" -> {
                if (StringUtils.hasText(rule.getOperator())) {
                    throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                            "OFFLINE 规则不允许设置比较符");
                }
                if (rule.getThreshold() == null
                        || rule.getThreshold().compareTo(OFFLINE_MIN_MINUTES) < 0
                        || rule.getThreshold().compareTo(OFFLINE_MAX_MINUTES) > 0) {
                    throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                            "OFFLINE 规则阈值必须为 1-1440 分钟");
                }
            }
            case "COMM" -> {
                if (StringUtils.hasText(rule.getOperator()) || rule.getThreshold() != null) {
                    throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                            "COMM 规则不允许设置比较符与阈值");
                }
            }
            default -> throw new BusinessException(ResultCode.ALARM_RULE_INVALID,
                    "未知规则类型: " + rule.getRuleType());
        }
    }

    private void validateRuleDuplicate(DevicePointAlarmRule rule, Long excludeId) {
        LambdaQueryWrapper<DevicePointAlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DevicePointAlarmRule::getTemplateItemId, rule.getTemplateItemId());
        if (rule.getPointId() == null) {
            wrapper.isNull(DevicePointAlarmRule::getPointId);
        } else {
            wrapper.eq(DevicePointAlarmRule::getPointId, rule.getPointId());
        }
        if (rule.getOperator() == null) {
            wrapper.isNull(DevicePointAlarmRule::getOperator);
        } else {
            wrapper.eq(DevicePointAlarmRule::getOperator, rule.getOperator());
        }
        if (rule.getDeviceId() == null) {
            wrapper.isNull(DevicePointAlarmRule::getDeviceId);
        } else {
            wrapper.eq(DevicePointAlarmRule::getDeviceId, rule.getDeviceId());
        }
        if (excludeId != null) {
            wrapper.ne(DevicePointAlarmRule::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.ALARM_RULE_DUPLICATE,
                    "同一模板指标项+点位+比较符的规则已存在");
        }
    }

    private List<Long> listTemplateItemIdsByType(Integer typeId) {
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId);
        return templateMapper.selectList(wrapper).stream()
                .map(DeviceTypePointTemplate::getId)
                .collect(Collectors.toList());
    }

    private void fillPointFields(List<PointAlarmRuleVO> vos) {
        Set<Long> pointIds = vos.stream()
                .map(PointAlarmRuleVO::getPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (pointIds.isEmpty()) {
            return;
        }
        Map<Long, PointDefinition> pointById = pointDefinitionService.listByIds(pointIds).stream()
                .collect(Collectors.toMap(PointDefinition::getId, Function.identity()));
        for (PointAlarmRuleVO vo : vos) {
            PointDefinition point = pointById.get(vo.getPointId());
            if (point != null) {
                vo.setPointCode(point.getPointCode());
                vo.setPointName(point.getPointName());
                vo.setUnit(point.getUnit());
            }
        }
    }
}
