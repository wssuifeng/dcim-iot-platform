package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.service.DeviceTypePointTemplateService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:设备类型点位模板服务实现类（双层：平台级默认 + 组织自定义）
 *
 * 【模板覆盖写策略】
 * 1. 校验 typeId 对应的 device_types 存在且为五类产品类型
 * 2. 校验每个 pointId 在 point_definition 中存在
 * 3. 先移除同组织层旧模板（逻辑删除，不影响另一层）
 * 4. 批量插入新模板（携带 organization_id 归属）
 *
 * 【双层生效解析】组织级整体存在则用组织级，否则降级平台级（D07）
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTypePointTemplateServiceImpl
        extends ServiceImpl<DeviceTypePointTemplateMapper, DeviceTypePointTemplate>
        implements DeviceTypePointTemplateService {

    private static final long PLATFORM_ORG = 0L;

    private final DeviceTypeMapper deviceTypeMapper;
    private final PointDefinitionService pointDefinitionService;
    private final CurrentOrganizationContext currentOrganizationContext;

    @Override
    public List<DeviceTypePointTemplate> listTemplates(Integer typeId) {
        Long orgId = currentOrganizationContext.currentOrganizationId().orElse(null);
        return listEffectiveTemplates(typeId, orgId);
    }

    @Override
    public List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId, Long organizationId) {
        ensureTypeExists(typeId);
        if (organizationId != null && organizationId != PLATFORM_ORG) {
            List<DeviceTypePointTemplate> orgTemplates = listByOrganization(typeId, organizationId);
            if (!orgTemplates.isEmpty()) {
                return orgTemplates;
            }
        }
        return listByOrganization(typeId, PLATFORM_ORG);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO) {
        Long orgId = currentOrganizationContext.currentOrganizationId().orElse(PLATFORM_ORG);
        return saveTemplatesForOrganization(typeId, batchDTO, orgId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplatesForOrganization(Integer typeId,
                                                                      DeviceTypePointTemplateBatchDTO batchDTO,
                                                                      Long organizationId) {
        ensureTypeExists(typeId);
        List<DeviceTypePointTemplateDTO> items = batchDTO.getItems();
        validateTemplateItems(items);

        // 1. 清空同组织层旧模板（逻辑删除，不影响另一层）
        LambdaQueryWrapper<DeviceTypePointTemplate> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(DeviceTypePointTemplate::getTypeId, typeId)
                .eq(DeviceTypePointTemplate::getOrganizationId, organizationId);
        remove(removeWrapper);

        // 2. 批量保存新模板（携带组织归属）
        List<DeviceTypePointTemplate> newTemplates = new ArrayList<>(items.size());
        for (DeviceTypePointTemplateDTO item : items) {
            DeviceTypePointTemplate template = new DeviceTypePointTemplate();
            template.setTypeId(typeId);
            template.setPointId(item.getPointId());
            template.setOrganizationId(organizationId);
            template.setIsRequired(item.getIsRequired() != null ? item.getIsRequired() : 0);
            template.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            save(template);
            newTemplates.add(template);
        }
        log.info("设备类型点位模板覆盖保存完成 typeId={} organizationId={} count={}",
                typeId, organizationId, newTemplates.size());
        return newTemplates;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAllTemplates(Integer typeId) {
        Long orgId = currentOrganizationContext.currentOrganizationId().orElse(PLATFORM_ORG);
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId)
                .eq(DeviceTypePointTemplate::getOrganizationId, orgId);
        remove(wrapper);
        log.info("已清空设备类型 {} 组织层 {} 的点位模板", typeId, orgId);
    }

    // ===================== 内部辅助 =====================

    private List<DeviceTypePointTemplate> listByOrganization(Integer typeId, Long organizationId) {
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId)
                .eq(DeviceTypePointTemplate::getOrganizationId, organizationId)
                .orderByAsc(DeviceTypePointTemplate::getSortOrder);
        return list(wrapper);
    }

    private void ensureTypeExists(Integer typeId) {
        DeviceType deviceType = deviceTypeMapper.selectById(typeId);
        if (deviceType == null) {
            throw new BusinessException(ResultCode.DEVICE_TYPE_NOT_FOUND, "设备类型不存在: " + typeId);
        }
        if (!DevicePointMetricContract.isSupportedDeviceType(deviceType.getTypeCode())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "点位模板仅支持 UPS/PDU_METER/PRECISION_AC/TEMP_HUMIDITY/LEAK_DETECTOR");
        }
    }

    private void validateTemplateItems(List<DeviceTypePointTemplateDTO> items) {
        List<Long> seenPointIds = new ArrayList<>();
        for (DeviceTypePointTemplateDTO item : items) {
            // 校验点位存在，否则拒绝整批写入
            pointDefinitionService.getPointDefinitionOrThrow(item.getPointId());
            if (seenPointIds.contains(item.getPointId())) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "模板项中点位定义ID重复: " + item.getPointId());
            }
            seenPointIds.add(item.getPointId());
        }
    }
}
