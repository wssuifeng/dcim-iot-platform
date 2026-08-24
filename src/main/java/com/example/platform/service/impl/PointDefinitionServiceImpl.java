package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.dto.PointResourceExportPackage;
import com.example.platform.entity.DevicePointAlarmRule;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.mapper.DevicePointBindingMapper;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
import com.example.platform.mapper.PointDefinitionMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * 类功能:点位定义服务实现类（双层：平台级全局 + 组织私有）
 *
 * 【归属与可见性】organization_id=0 平台级全局共享；>0 组织私有（仅本组织可见）。
 * 【编码唯一】(organization_id, point_code) 唯一，组织私有编码允许与平台级同名
 *   （引用时以主键区分，编码仅展示与导入导出关联用）。
 * 【删除保护】点位被模板/绑定/规则任一引用时拒绝删除（4035）。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointDefinitionServiceImpl extends ServiceImpl<PointDefinitionMapper, PointDefinition>
        implements PointDefinitionService {

    private static final long PLATFORM_ORG = 0L;

    private final DataScopeService dataScopeService;
    private final CurrentOrganizationContext currentOrganizationContext;
    private final DeviceTypeMapper deviceTypeMapper;
    private final DeviceTypePointTemplateMapper templateMapper;
    private final DevicePointBindingMapper bindingMapper;
    private final DevicePointAlarmRuleMapper alarmRuleMapper;

    // 注：不注入 DeviceTypePointTemplateService——该服务反向依赖本服务校验点位，
    // 注入会形成循环依赖；模板读写一律走 Mapper 层完成。

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointDefinition createPointDefinition(PointDefinitionCreateDTO createDTO, Long currentUserId) {
        long organizationId = resolveCreateOrganization(currentUserId);
        checkPointCodeUnique(createDTO.getPointCode(), organizationId);
        PointDefinition point = new PointDefinition();
        point.setPointCode(createDTO.getPointCode());
        point.setPointName(createDTO.getPointName());
        point.setUnit(createDTO.getUnit());
        String dataType = StringUtils.hasText(createDTO.getDataType())
                ? createDTO.getDataType().trim().toUpperCase() : "NUMBER";
        if (!DevicePointMetricContract.POINT_DATA_TYPES.contains(dataType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "数据类型仅允许 NUMBER/BOOLEAN/STRING");
        }
        point.setDataType(dataType);
        point.setDescription(createDTO.getDescription());
        point.setOrganizationId(organizationId);
        if (!save(point)) {
            throw new BusinessException("创建点位定义失败，请稍后重试");
        }
        log.info("点位定义创建成功 pointCode={} organizationId={} operator={}",
                point.getPointCode(), organizationId, currentUserId);
        return point;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointDefinition createPlatformPointDefinition(PointDefinitionCreateDTO createDTO, Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.isPlatformAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅平台管理员可创建平台级点位");
        }
        checkPointCodeUnique(createDTO.getPointCode(), PLATFORM_ORG);
        PointDefinition point = new PointDefinition();
        point.setPointCode(createDTO.getPointCode());
        point.setPointName(createDTO.getPointName());
        point.setUnit(createDTO.getUnit());
        String dataType = StringUtils.hasText(createDTO.getDataType())
                ? createDTO.getDataType().trim().toUpperCase() : "NUMBER";
        if (!DevicePointMetricContract.POINT_DATA_TYPES.contains(dataType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "数据类型仅允许 NUMBER/BOOLEAN/STRING");
        }
        point.setDataType(dataType);
        point.setDescription(createDTO.getDescription());
        point.setOrganizationId(PLATFORM_ORG);
        if (!save(point)) {
            throw new BusinessException("创建平台级点位定义失败，请稍后重试");
        }
        return point;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointDefinition updatePointDefinition(Long id, PointDefinitionUpdateDTO updateDTO, Long currentUserId) {
        PointDefinition point = getPointDefinitionOrThrow(id);
        ensureWriteAccess(point, currentUserId);
        if (updateDTO.getPointName() != null) {
            point.setPointName(updateDTO.getPointName());
        }
        if (updateDTO.getUnit() != null) {
            point.setUnit(updateDTO.getUnit());
        }
        if (updateDTO.getDataType() != null) {
            String dataType = updateDTO.getDataType().trim().toUpperCase();
            if (!DevicePointMetricContract.POINT_DATA_TYPES.contains(dataType)) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "数据类型仅允许 NUMBER/BOOLEAN/STRING");
            }
            point.setDataType(dataType);
        }
        if (updateDTO.getDescription() != null) {
            point.setDescription(updateDTO.getDescription());
        }
        if (!updateById(point)) {
            throw new BusinessException("更新点位定义失败");
        }
        return point;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePointDefinition(Long id, Long currentUserId) {
        PointDefinition point = getPointDefinitionOrThrow(id);
        ensureWriteAccess(point, currentUserId);
        ensureNotReferenced(id);
        removeById(id);
        log.info("点位定义删除 pointId={} pointCode={} operator={}", id, point.getPointCode(), currentUserId);
    }

    @Override
    public PointDefinition getPointDefinitionOrThrow(Long id) {
        PointDefinition point = getById(id);
        if (point == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "点位定义不存在: " + id);
        }
        return point;
    }

    @Override
    public PointDefinition getPointDefinitionOrThrow(Long id, Long currentUserId) {
        PointDefinition point = getPointDefinitionOrThrow(id);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        ensurePointVisible(point, context);
        return point;
    }

    @Override
    public Page<PointDefinition> pagePointDefinitions(Long currentUserId, Integer current, Integer size,
                                                      String keyword, String dataType) {
        int[] params = PaginationUtils.validate(current, size);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        Set<Long> visibleOrgs = new HashSet<>();
        visibleOrgs.add(PLATFORM_ORG);
        if (!context.isPlatformAdmin()) {
            if (context.getOrganizationIds().isEmpty()) {
                Page<PointDefinition> empty = new Page<>(params[0], params[1]);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
            visibleOrgs.addAll(context.getOrganizationIds());
        }
        wrapper.in(PointDefinition::getOrganizationId, visibleOrgs);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PointDefinition::getPointCode, keyword.trim())
                    .or().like(PointDefinition::getPointName, keyword.trim()));
        }
        if (StringUtils.hasText(dataType)) {
            wrapper.eq(PointDefinition::getDataType, dataType.trim());
        }
        wrapper.orderByAsc(PointDefinition::getOrganizationId)
                .orderByDesc(PointDefinition::getCreateTime);
        return page(new Page<>(params[0], params[1]), wrapper);
    }

    @Override
    public List<PointDefinition> listAvailablePoints(Long organizationId) {
        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        if (organizationId != null) {
            wrapper.in(PointDefinition::getOrganizationId, List.of(PLATFORM_ORG, organizationId));
        } else {
            wrapper.eq(PointDefinition::getOrganizationId, PLATFORM_ORG);
        }
        wrapper.orderByAsc(PointDefinition::getPointCode);
        return list(wrapper);
    }

    @Override
    public List<PointDefinition> listAvailablePoints(Long organizationId, Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        Long targetOrganizationId = organizationId != null
                ? organizationId : currentOrganizationContext.currentOrganizationId().orElse(null);
        if (targetOrganizationId != null && targetOrganizationId != PLATFORM_ORG
                && !context.isPlatformAdmin()
                && !context.getOrganizationIds().contains(targetOrganizationId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查询其他组织的点位");
        }
        if (targetOrganizationId == null || targetOrganizationId == PLATFORM_ORG) {
            return listAvailablePoints(null);
        }
        return listAvailablePoints(targetOrganizationId);
    }

    @Override
    public PointResourceExportPackage exportOrgPackage(Long organizationId) {
        PointResourceExportPackage pkg = new PointResourceExportPackage();
        LambdaQueryWrapper<PointDefinition> pointWrapper = new LambdaQueryWrapper<>();
        pointWrapper.eq(PointDefinition::getOrganizationId, organizationId)
                .orderByAsc(PointDefinition::getPointCode);
        List<PointDefinition> points = list(pointWrapper);
        pkg.setPoints(points.stream().map(p -> {
            PointResourceExportPackage.PointItem item = new PointResourceExportPackage.PointItem();
            item.setPointCode(p.getPointCode());
            item.setPointName(p.getPointName());
            item.setUnit(p.getUnit());
            item.setDataType(p.getDataType());
            item.setDescription(p.getDescription());
            return item;
        }).collect(Collectors.toList()));

        LambdaQueryWrapper<DeviceTypePointTemplate> templateWrapper = new LambdaQueryWrapper<>();
        templateWrapper.eq(DeviceTypePointTemplate::getOrganizationId, organizationId);
        List<DeviceTypePointTemplate> templates = templateMapper.selectList(templateWrapper);
        Map<Integer, String> typeCodeById = deviceTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceType::getId, DeviceType::getTypeCode));
        Map<String, PointDefinition> pointByCode = points.stream()
                .collect(Collectors.toMap(PointDefinition::getPointCode, Function.identity()));

        // 按设备类型聚合模板条目
        Map<Integer, List<DeviceTypePointTemplate>> byType = templates.stream()
                .collect(Collectors.groupingBy(DeviceTypePointTemplate::getTypeId));
        List<PointResourceExportPackage.TemplateItem> templateItems = new ArrayList<>();
        for (Map.Entry<Integer, List<DeviceTypePointTemplate>> entry : byType.entrySet()) {
            PointResourceExportPackage.TemplateItem templateItem = new PointResourceExportPackage.TemplateItem();
            templateItem.setTypeCode(typeCodeById.get(entry.getKey()));
            if (templateItem.getTypeCode() == null) {
                continue;
            }
            templateItem.setItems(entry.getValue().stream()
                    .filter(t -> pointByCode.containsKey(
                            pointById(t.getPointId()) != null ? pointById(t.getPointId()).getPointCode() : ""))
                    .map(t -> {
                        PointResourceExportPackage.TemplatePointRef ref = new PointResourceExportPackage.TemplatePointRef();
                        PointDefinition point = pointById(t.getPointId());
                        ref.setPointCode(point != null ? point.getPointCode() : null);
                        ref.setRequired(t.getIsRequired() != null && t.getIsRequired() == 1);
                        ref.setSortOrder(t.getSortOrder());
                        return ref;
                    })
                    .filter(ref -> ref.getPointCode() != null)
                    .collect(Collectors.toList()));
            templateItems.add(templateItem);
        }
        pkg.setTemplates(templateItems);
        return pkg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importPackage(PointResourceExportPackage pkg, Long targetOrganizationId, Long currentUserId) {
        if (pkg == null || pkg.getPoints() == null || pkg.getPoints().isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "导入包为空或缺少点位数据");
        }
        // 1. 导入点位（编码冲突时重映射为 原编码_org{orgId}）
        Map<String, Long> codeToId = new HashMap<>();
        for (PointResourceExportPackage.PointItem item : pkg.getPoints()) {
            String code = item.getPointCode();
            LambdaQueryWrapper<PointDefinition> exist = new LambdaQueryWrapper<>();
            exist.eq(PointDefinition::getOrganizationId, targetOrganizationId)
                    .eq(PointDefinition::getPointCode, code);
            PointDefinition existing = getOne(exist, false);
            if (existing != null) {
                codeToId.put(code, existing.getId());
                continue;
            }
            PointDefinitionCreateDTO createDTO = new PointDefinitionCreateDTO();
            createDTO.setPointCode(code);
            createDTO.setPointName(item.getPointName());
            createDTO.setUnit(item.getUnit());
            createDTO.setDataType(item.getDataType());
            createDTO.setDescription(item.getDescription());
            PointDefinition created = importPoint(createDTO, targetOrganizationId);
            codeToId.put(code, created.getId());
        }

        // 2. 导入模板（类型编码 → 目标组织层覆盖写；走 Mapper 避免与模板服务循环依赖）
        if (pkg.getTemplates() != null) {
            for (PointResourceExportPackage.TemplateItem templateItem : pkg.getTemplates()) {
                DeviceType type = findTypeByCode(templateItem.getTypeCode());
                if (type == null) {
                    continue;
                }
                LambdaQueryWrapper<DeviceTypePointTemplate> removeWrapper = new LambdaQueryWrapper<>();
                removeWrapper.eq(DeviceTypePointTemplate::getTypeId, type.getId())
                        .eq(DeviceTypePointTemplate::getOrganizationId, targetOrganizationId);
                templateMapper.delete(removeWrapper);
                if (templateItem.getItems() == null) {
                    continue;
                }
                for (PointResourceExportPackage.TemplatePointRef ref : templateItem.getItems()) {
                    Long pointId = codeToId.get(ref.getPointCode());
                    if (pointId == null) {
                        continue;
                    }
                    DeviceTypePointTemplate template = new DeviceTypePointTemplate();
                    template.setTypeId(type.getId());
                    template.setPointId(pointId);
                    template.setOrganizationId(targetOrganizationId);
                    template.setIsRequired(Boolean.TRUE.equals(ref.getRequired()) ? 1 : 0);
                    template.setSortOrder(ref.getSortOrder() != null ? ref.getSortOrder() : 0);
                    templateMapper.insert(template);
                }
            }
        }
        log.info("点位资源包导入完成 targetOrg={} points={} operator={}",
                targetOrganizationId, pkg.getPoints().size(), currentUserId);
    }

    // ===================== 内部辅助 =====================

    private PointDefinition importPoint(PointDefinitionCreateDTO createDTO, long organizationId) {
        PointDefinition point = new PointDefinition();
        point.setPointCode(createDTO.getPointCode());
        point.setPointName(createDTO.getPointName());
        point.setUnit(createDTO.getUnit());
        String dataType = StringUtils.hasText(createDTO.getDataType())
                ? createDTO.getDataType().trim().toUpperCase() : "NUMBER";
        point.setDataType(dataType);
        point.setDescription(createDTO.getDescription());
        point.setOrganizationId(organizationId);
        save(point);
        return point;
    }

    private long resolveCreateOrganization(Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.hasWritePermission()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只读范围用户禁止创建点位定义");
        }
        Long orgId = currentOrganizationContext.currentOrganizationId().orElse(null);
        if (context.isPlatformAdmin()) {
            if (orgId != null) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "平台管理员不可创建组织级点位（D26）");
            }
            return PLATFORM_ORG;
        }
        // 组织级点位：仅当前组织 ORG_ADMIN 可创建（OPERATOR/VIEWER 一律拒绝）
        if (orgId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "组织用户创建点位必须携带组织上下文（X-Organization-Id）");
        }
        if (!context.isOrgAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "仅当前组织管理员可创建组织级点位");
        }
        if (!context.getOrganizationIds().contains(orgId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "无权在其他组织创建点位");
        }
        return orgId;
    }

    private void ensureWriteAccess(PointDefinition point, Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        long orgId = point.getOrganizationId() != null ? point.getOrganizationId() : PLATFORM_ORG;
        if (orgId == PLATFORM_ORG) {
            if (!context.isPlatformAdmin()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "平台级点位仅平台管理员可修改");
            }
            return;
        }
        if (context.isPlatformAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "平台管理员不可修改组织级资源（D26）");
        }
        if (!context.hasWritePermission()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只读范围用户禁止修改点位定义");
        }
        if (!context.isOrgAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅当前组织管理员可修改组织级点位");
        }
        if (!context.getOrganizationIds().contains(orgId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改其他组织的私有点位");
        }
    }

    private void ensurePointVisible(PointDefinition point, DataScopeContext context) {
        long pointOrg = point.getOrganizationId() != null ? point.getOrganizationId() : PLATFORM_ORG;
        if (pointOrg != PLATFORM_ORG && !context.getOrganizationIds().contains(pointOrg)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他组织的私有点位");
        }
    }

    private void ensureNotReferenced(Long pointId) {
        LambdaQueryWrapper<DeviceTypePointTemplate> templateWrapper = new LambdaQueryWrapper<>();
        templateWrapper.eq(DeviceTypePointTemplate::getPointId, pointId);
        if (templateMapper.selectCount(templateWrapper) > 0) {
            throw new BusinessException(ResultCode.POINT_IN_USE, "点位被类型模板引用，禁止删除");
        }
        LambdaQueryWrapper<DevicePointBinding> bindingWrapper = new LambdaQueryWrapper<>();
        bindingWrapper.eq(DevicePointBinding::getPointId, pointId);
        if (bindingMapper.selectCount(bindingWrapper) > 0) {
            throw new BusinessException(ResultCode.POINT_IN_USE, "点位被设备绑定引用，禁止删除");
        }
        LambdaQueryWrapper<DevicePointAlarmRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(DevicePointAlarmRule::getPointId, pointId);
        if (alarmRuleMapper.selectCount(ruleWrapper) > 0) {
            throw new BusinessException(ResultCode.POINT_IN_USE, "点位被告警规则引用，禁止删除");
        }
    }

    private void checkPointCodeUnique(String pointCode, long organizationId) {
        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointDefinition::getOrganizationId, organizationId)
                .eq(PointDefinition::getPointCode, pointCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.POINT_CODE_EXISTS,
                    "点位编码已存在: " + pointCode);
        }
    }

    private DeviceType findTypeByCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return null;
        }
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeCode, typeCode.trim().toUpperCase());
        return deviceTypeMapper.selectOne(wrapper);
    }

    private PointDefinition pointById(Long id) {
        if (id == null) {
            return null;
        }
        return getById(id);
    }
}
