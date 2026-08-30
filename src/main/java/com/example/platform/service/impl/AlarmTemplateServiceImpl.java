package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlarmTemplateItemCreateDTO;
import com.example.platform.dto.AlarmTemplateItemMechanismDTO;
import com.example.platform.dto.AlarmTemplateItemUpdateDTO;
import com.example.platform.dto.AlarmTemplateItemVO;
import com.example.platform.dto.AlarmTemplateSaveDTO;
import com.example.platform.dto.AlarmTemplateVO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.mapper.AlarmTemplateMapper;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * 类功能:告警模版服务实现类——模版与规则行CRUD、设备生效规则解析（设备改绑→类型基础模版两级）
 *
 * 【生效解析】
 *   1. 设备.alarmTemplateId 非空 → 该模版（改绑）
 *   2. 否则 → 设备类型.alarmTemplateId（基础模版）
 *   3. 生效模版 status=0 且规则行 enabled=1 → 生效规则集
 *   4. 改绑与跟随在 source 标记上区分（DEVICE_OVERRIDE/TEMPLATE），供前端展示生效层级
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmTemplateServiceImpl
        extends ServiceImpl<AlarmTemplateMapper, AlarmTemplate>
        implements AlarmTemplateService {

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int ENABLED_ON = 1;
    private static final int ENABLED_OFF = 0;
    private static final BigDecimal OFFLINE_MIN_MINUTES = BigDecimal.ONE;
    private static final BigDecimal OFFLINE_MAX_MINUTES = new BigDecimal("1440");

    private final AlarmTemplateItemMapper itemMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final PointDefinitionService pointDefinitionService;

    @Override
    public Page<AlarmTemplateVO> pageTemplates(Integer current, Integer size, String keyword, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        LambdaQueryWrapper<AlarmTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AlarmTemplate::getTemplateName, keyword.trim())
                    .or().like(AlarmTemplate::getTemplateCode, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(AlarmTemplate::getStatus, status);
        }
        wrapper.orderByDesc(AlarmTemplate::getUpdateTime);
        Page<AlarmTemplate> source = page(new Page<>(params[0], params[1]), wrapper);

        Page<AlarmTemplateVO> result = new Page<>(params[0], params[1]);
        result.setTotal(source.getTotal());
        result.setRecords(source.getRecords().stream()
                .map(AlarmTemplateVO::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new)));
        fillRefCounts(result.getRecords());
        return result;
    }

    @Override
    public AlarmTemplateVO getTemplate(Long id) {
        AlarmTemplateVO vo = AlarmTemplateVO.fromEntity(getTemplateOrThrow(id));
        fillRefCounts(List.of(vo));
        return vo;
    }

    @Override
    public List<AlarmTemplateItemVO> listItems(Long templateId) {
        getTemplateOrThrow(templateId);
        LambdaQueryWrapper<AlarmTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplateItem::getTemplateId, templateId)
                .orderByAsc(AlarmTemplateItem::getSortOrder)
                .orderByAsc(AlarmTemplateItem::getId);
        List<AlarmTemplateItemVO> items = itemMapper.selectList(wrapper).stream()
                .map(AlarmTemplateItemVO::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
        fillPointFields(items);
        // 指标分组内按级别升序（最严重在前），跨指标按指标ID稳定排序
        items.sort(Comparator
                .comparing(AlarmTemplateItemVO::getPointId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AlarmTemplateItemVO::getAlarmLevel));
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTemplateVO createTemplate(AlarmTemplateSaveDTO dto) {
        checkCodeUnique(dto.getTemplateCode(), null);
        AlarmTemplate template = new AlarmTemplate();
        template.setTemplateCode(dto.getTemplateCode().trim());
        template.setTemplateName(dto.getTemplateName().trim());
        template.setDescription(dto.getDescription());
        template.setStatus(Boolean.FALSE.equals(dto.getStatus()) ? STATUS_DISABLED : STATUS_ENABLED);
        if (!save(template)) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "创建告警模版失败");
        }
        log.info("告警模版创建 templateId={} code={}", template.getId(), template.getTemplateCode());
        return AlarmTemplateVO.fromEntity(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTemplateVO updateTemplate(Long id, AlarmTemplateSaveDTO dto) {
        AlarmTemplate template = getTemplateOrThrow(id);
        checkCodeUnique(dto.getTemplateCode(), id);
        template.setTemplateCode(dto.getTemplateCode().trim());
        template.setTemplateName(dto.getTemplateName().trim());
        template.setDescription(dto.getDescription());
        if (dto.getStatus() != null) {
            template.setStatus(Boolean.TRUE.equals(dto.getStatus()) ? STATUS_ENABLED : STATUS_DISABLED);
        }
        if (!updateById(template)) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "更新告警模版失败");
        }
        return AlarmTemplateVO.fromEntity(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        getTemplateOrThrow(id);
        long typeRefs = countTypeRefs(id);
        long deviceRefs = countDeviceRefs(id);
        if (typeRefs + deviceRefs > 0) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_IN_USE,
                    String.format("模版被绑定引用（类型%d个、设备%d台），请先解绑后再删除", typeRefs, deviceRefs));
        }
        LambdaQueryWrapper<AlarmTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplateItem::getTemplateId, id);
        itemMapper.delete(wrapper);
        removeById(id);
        log.info("告警模版删除 templateId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTemplateVO duplicateTemplate(Long id, String newCode, String newName) {
        AlarmTemplate source = getTemplateOrThrow(id);
        if (!StringUtils.hasText(newCode)) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "新模版编码不能为空");
        }
        checkCodeUnique(newCode.trim(), null);
        AlarmTemplate copy = new AlarmTemplate();
        copy.setTemplateCode(newCode.trim());
        copy.setTemplateName(StringUtils.hasText(newName) ? newName.trim() : source.getTemplateName() + "-副本");
        copy.setDescription(source.getDescription());
        copy.setStatus(source.getStatus());
        if (!save(copy)) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "复制告警模版失败");
        }
        LambdaQueryWrapper<AlarmTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplateItem::getTemplateId, id);
        for (AlarmTemplateItem item : itemMapper.selectList(wrapper)) {
            AlarmTemplateItem copyItem = new AlarmTemplateItem();
            copyItem.setTemplateId(copy.getId());
            copyItem.setPointId(item.getPointId());
            copyItem.setRuleType(item.getRuleType());
            copyItem.setRuleName(item.getRuleName());
            copyItem.setOperator(item.getOperator());
            copyItem.setThreshold(item.getThreshold());
            copyItem.setBreachCount(item.getBreachCount());
            copyItem.setRateLimit(item.getRateLimit());
            copyItem.setRateWindowSec(item.getRateWindowSec());
            copyItem.setReAlarmSec(item.getReAlarmSec());
            copyItem.setHysteresis(item.getHysteresis());
            copyItem.setAlarmLevel(item.getAlarmLevel());
            copyItem.setEnabled(item.getEnabled());
            copyItem.setSortOrder(item.getSortOrder());
            itemMapper.insert(copyItem);
        }
        log.info("告警模版复制 sourceId={} targetId={}", id, copy.getId());
        return AlarmTemplateVO.fromEntity(copy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTemplateItemVO createItem(Long templateId, AlarmTemplateItemCreateDTO dto) {
        getTemplateOrThrow(templateId);
        AlarmTemplateItem item = new AlarmTemplateItem();
        item.setTemplateId(templateId);
        item.setRuleType(dto.getRuleType().trim().toUpperCase());
        item.setRuleName(dto.getRuleName().trim());
        item.setOperator(StringUtils.hasText(dto.getOperator()) ? dto.getOperator().trim().toUpperCase() : null);
        item.setThreshold(dto.getThreshold());
        item.setAlarmLevel(dto.getAlarmLevel());
        item.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? ENABLED_ON : ENABLED_OFF);
        item.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        // METRIC 规则必须显式指定指标；通用规则（OFFLINE/COMM）指标为空
        item.setPointId("METRIC".equals(item.getRuleType()) ? dto.getPointId() : null);
        applyMechanismGroup(item, dto.getMechanism());
        normalizeMechanismFields(item);
        validateItemShape(item);
        checkItemDuplicate(item, null);
        if (itemMapper.insert(item) <= 0) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "创建规则行失败");
        }
        AlarmTemplateItemVO vo = AlarmTemplateItemVO.fromEntity(item);
        fillPointFields(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTemplateItemVO updateItem(Long itemId, AlarmTemplateItemUpdateDTO dto) {
        AlarmTemplateItem item = getItemOrThrow(itemId);
        item.setRuleName(dto.getRuleName().trim());
        item.setOperator(StringUtils.hasText(dto.getOperator()) ? dto.getOperator().trim().toUpperCase() : null);
        item.setThreshold(dto.getThreshold());
        item.setAlarmLevel(dto.getAlarmLevel());
        if (dto.getEnabled() != null) {
            item.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? ENABLED_ON : ENABLED_OFF);
        }
        if (dto.getSortOrder() != null) {
            item.setSortOrder(dto.getSortOrder());
        }
        // 机制字段整组语义：不传=保持现值（如启用开关的部分更新）；传了=整组替换，组内 null=禁用该项
        if (dto.getMechanism() != null) {
            applyMechanismGroup(item, dto.getMechanism());
        }
        normalizeMechanismFields(item);
        validateItemShape(item);
        checkItemDuplicate(item, itemId);
        if (itemMapper.updateById(item) <= 0) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID, "更新规则行失败");
        }
        if (item.getRateLimit() == null) {
            // updateById 忽略 null 字段（MyBatis-Plus 行为）：关闭 M2 需显式 set null 清库
            LambdaUpdateWrapper<AlarmTemplateItem> clearRate = new LambdaUpdateWrapper<>();
            clearRate.eq(AlarmTemplateItem::getId, itemId)
                    .set(AlarmTemplateItem::getRateLimit, null);
            itemMapper.update(null, clearRate);
        }        AlarmTemplateItemVO vo = AlarmTemplateItemVO.fromEntity(item);
        fillPointFields(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long itemId) {
        getItemOrThrow(itemId);
        itemMapper.deleteById(itemId);
        log.info("告警模版规则行删除 itemId={}", itemId);
    }

    @Override
    public List<PointAlarmRuleVO> findEffectiveRules(Integer deviceId, Long pointId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        AlarmTemplate template = resolveEffectiveTemplate(device);
        if (template == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<AlarmTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplateItem::getTemplateId, template.getId())
                .eq(AlarmTemplateItem::getEnabled, ENABLED_ON);
        if (pointId != null) {
            // 指定指标时同时命中该指标 METRIC 规则与通用规则（point_id 为空）
            wrapper.and(w -> w.eq(AlarmTemplateItem::getPointId, pointId)
                    .or().isNull(AlarmTemplateItem::getPointId));
        }
        List<AlarmTemplateItem> items = itemMapper.selectList(wrapper);
        if (items.isEmpty()) {
            return new ArrayList<>();
        }
        String source = Objects.equals(device.getAlarmTemplateId(), template.getId())
                ? PointAlarmRuleVO.SOURCE_DEVICE_OVERRIDE : PointAlarmRuleVO.SOURCE_TEMPLATE;
        List<PointAlarmRuleVO> result = items.stream()
                .map(item -> toRuleVO(item, source))
                .collect(Collectors.toCollection(ArrayList::new));
        result.sort(Comparator
                .comparing(PointAlarmRuleVO::getPointId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PointAlarmRuleVO::getAlarmLevel));
        return result;
    }

    @Override
    public AlarmTemplate resolveEffectiveTemplate(Device device) {
        if (device.getAlarmTemplateId() != null) {
            AlarmTemplate bound = getById(device.getAlarmTemplateId());
            if (bound != null) {
                return bound;
            }
        }
        DeviceType type = deviceTypeMapper.selectById(device.getTypeId());
        if (type == null || type.getAlarmTemplateId() == null) {
            return null;
        }
        return getById(type.getAlarmTemplateId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTypeBinding(Integer typeId, Long alarmTemplateId) {
        DeviceType type = deviceTypeMapper.selectById(typeId);
        if (type == null) {
            throw new BusinessException(ResultCode.DEVICE_TYPE_NOT_FOUND, "设备类型不存在: " + typeId);
        }
        if (alarmTemplateId != null) {
            AlarmTemplate template = getTemplateOrThrow(alarmTemplateId);
            if (template.getStatus() != null && template.getStatus() != STATUS_ENABLED) {
                throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                        "停用状态的模版不能绑定为类型基础模版: " + template.getTemplateName());
            }
        }
        // 解绑须显式 set null（updateById 默认忽略 null 字段）
        LambdaUpdateWrapper<DeviceType> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DeviceType::getId, typeId)
                .set(DeviceType::getAlarmTemplateId, alarmTemplateId);
        deviceTypeMapper.update(null, wrapper);
        log.info("设备类型告警模版绑定 typeId={} templateId={}", typeId, alarmTemplateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceBinding(Integer deviceId, Long alarmTemplateId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        if (alarmTemplateId != null) {
            AlarmTemplate template = getTemplateOrThrow(alarmTemplateId);
            if (template.getStatus() != null && template.getStatus() != STATUS_ENABLED) {
                throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                        "停用状态的模版不能绑定到设备: " + template.getTemplateName());
            }
        }
        // 解绑须显式 set null（updateById 默认忽略 null 字段）
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, deviceId)
                .set(Device::getAlarmTemplateId, alarmTemplateId);
        deviceMapper.update(null, wrapper);
        log.info("设备告警模版改绑 deviceId={} templateId={}", deviceId, alarmTemplateId);
    }

    // ===================== 内部辅助 =====================

    private AlarmTemplate getTemplateOrThrow(Long id) {
        AlarmTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_NOT_FOUND, "告警模版不存在: " + id);
        }
        return template;
    }

    private AlarmTemplateItem getItemOrThrow(Long itemId) {
        AlarmTemplateItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_NOT_FOUND, "规则行不存在: " + itemId);
        }
        return item;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AlarmTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplate::getTemplateCode, code.trim());
        if (excludeId != null) {
            wrapper.ne(AlarmTemplate::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_CODE_EXISTS, "模版编码已存在: " + code);
        }
    }

    private void validateItemShape(AlarmTemplateItem item) {
        switch (item.getRuleType()) {
            case "METRIC" -> {
                if (item.getPointId() == null) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "METRIC 规则必须指定指标");
                }
                if (!StringUtils.hasText(item.getOperator()) || item.getThreshold() == null) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "METRIC 规则必须提供比较符与阈值");
                }
                if (pointDefinitionService.getById(item.getPointId()) == null) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "指标不存在: " + item.getPointId());
                }
            }
            case "OFFLINE" -> {
                if (StringUtils.hasText(item.getOperator())) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "OFFLINE 规则不允许设置比较符");
                }
                if (item.getThreshold() == null
                        || item.getThreshold().compareTo(OFFLINE_MIN_MINUTES) < 0
                        || item.getThreshold().compareTo(OFFLINE_MAX_MINUTES) > 0) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "OFFLINE 规则阈值必须为 1-1440 分钟");
                }
            }
            case "COMM" -> {
                if (StringUtils.hasText(item.getOperator()) || item.getThreshold() != null) {
                    throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                            "COMM 规则不允许设置比较符与阈值");
                }
            }
            default -> throw new BusinessException(ResultCode.ALARM_TEMPLATE_ITEM_INVALID,
                    "未知规则类型: " + item.getRuleType());
        }
    }

    private void checkItemDuplicate(AlarmTemplateItem item, Long excludeId) {
        LambdaQueryWrapper<AlarmTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmTemplateItem::getTemplateId, item.getTemplateId());
        if (item.getPointId() == null) {
            wrapper.isNull(AlarmTemplateItem::getPointId);
        } else {
            wrapper.eq(AlarmTemplateItem::getPointId, item.getPointId());
        }
        if (item.getOperator() == null) {
            wrapper.isNull(AlarmTemplateItem::getOperator);
        } else {
            wrapper.eq(AlarmTemplateItem::getOperator, item.getOperator());
        }
        if (excludeId != null) {
            wrapper.ne(AlarmTemplateItem::getId, excludeId);
        }
        if (itemMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.ALARM_RULE_DUPLICATE,
                    "同一模版下同指标同比较符的规则行已存在");
        }
    }

    private PointAlarmRuleVO toRuleVO(AlarmTemplateItem item, String source) {
        PointAlarmRuleVO vo = new PointAlarmRuleVO();
        vo.setId(item.getId());
        vo.setTemplateItemId(item.getTemplateId());
        vo.setPointId(item.getPointId());
        vo.setRuleType(item.getRuleType());
        vo.setRuleName(item.getRuleName());
        vo.setOperator(item.getOperator());
        vo.setThreshold(item.getThreshold());
        vo.setBreachCount(item.getBreachCount());
        vo.setRateLimit(item.getRateLimit());
        vo.setRateWindowSec(item.getRateWindowSec());
        vo.setReAlarmSec(item.getReAlarmSec());
        vo.setHysteresis(item.getHysteresis());
        vo.setAlarmLevel(item.getAlarmLevel());
        vo.setEnabled(item.getEnabled());
        vo.setSource(source);
        return vo;
    }

    // 机制字段组应用：组内 null 回"不启用"默认值（breach=0 / rate=NULL / window=60 / reAlarm=0 / hysteresis=0）
    private void applyMechanismGroup(AlarmTemplateItem item, AlarmTemplateItemMechanismDTO mechanism) {
        if (mechanism == null) {
            item.setBreachCount(0);
            item.setRateLimit(null);
            item.setRateWindowSec(60);
            item.setReAlarmSec(0);
            item.setHysteresis(BigDecimal.ZERO);
            return;
        }
        item.setBreachCount(mechanism.getBreachCount() != null ? mechanism.getBreachCount() : 0);
        item.setRateLimit(mechanism.getRateLimit());
        item.setRateWindowSec(mechanism.getRateWindowSec() != null ? mechanism.getRateWindowSec() : 60);
        item.setReAlarmSec(mechanism.getReAlarmSec() != null ? mechanism.getReAlarmSec() : 0);
        item.setHysteresis(mechanism.getHysteresis() != null ? mechanism.getHysteresis() : BigDecimal.ZERO);
    }

    // 机制字段仅对 METRIC 规则有意义：OFFLINE/COMM 强制回默认值，避免脏配置
    private void normalizeMechanismFields(AlarmTemplateItem item) {
        if (!"METRIC".equals(item.getRuleType())) {
            item.setBreachCount(0);
            item.setRateLimit(null);
            item.setRateWindowSec(60);
            item.setReAlarmSec(0);
            item.setHysteresis(BigDecimal.ZERO);
        }
    }

    private void fillRefCounts(List<AlarmTemplateVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> ids = vos.stream().map(AlarmTemplateVO::getId).toList();
        LambdaQueryWrapper<AlarmTemplateItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(AlarmTemplateItem::getTemplateId, ids);
        Map<Long, Long> itemCount = itemMapper.selectList(itemWrapper).stream()
                .collect(Collectors.groupingBy(AlarmTemplateItem::getTemplateId, Collectors.counting()));
        LambdaQueryWrapper<DeviceType> typeWrapper = new LambdaQueryWrapper<>();
        typeWrapper.in(DeviceType::getAlarmTemplateId, ids);
        Map<Long, Long> typeCount = deviceTypeMapper.selectList(typeWrapper).stream()
                .collect(Collectors.groupingBy(DeviceType::getAlarmTemplateId, Collectors.counting()));
        LambdaQueryWrapper<Device> deviceWrapper = new LambdaQueryWrapper<>();
        deviceWrapper.in(Device::getAlarmTemplateId, ids);
        Map<Long, Long> deviceCount = deviceMapper.selectList(deviceWrapper).stream()
                .collect(Collectors.groupingBy(Device::getAlarmTemplateId, Collectors.counting()));
        for (AlarmTemplateVO vo : vos) {
            vo.setItemCount(itemCount.getOrDefault(vo.getId(), 0L));
            vo.setTypeRefCount(typeCount.getOrDefault(vo.getId(), 0L));
            vo.setDeviceRefCount(deviceCount.getOrDefault(vo.getId(), 0L));
        }
    }

    private void fillPointFields(List<AlarmTemplateItemVO> vos) {
        Set<Long> pointIds = vos.stream()
                .map(AlarmTemplateItemVO::getPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (pointIds.isEmpty()) {
            return;
        }
        Map<Long, PointDefinition> pointById = pointDefinitionService.listByIds(pointIds).stream()
                .collect(Collectors.toMap(PointDefinition::getId, Function.identity()));
        for (AlarmTemplateItemVO vo : vos) {
            PointDefinition point = pointById.get(vo.getPointId());
            if (point != null) {
                vo.setPointCode(point.getPointCode());
                vo.setPointName(point.getPointName());
                vo.setUnit(point.getUnit());
            }
        }
    }

    private long countTypeRefs(Long templateId) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getAlarmTemplateId, templateId);
        return deviceTypeMapper.selectCount(wrapper);
    }

    private long countDeviceRefs(Long templateId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getAlarmTemplateId, templateId);
        return deviceMapper.selectCount(wrapper);
    }
}
