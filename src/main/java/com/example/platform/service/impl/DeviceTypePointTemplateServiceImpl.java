package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
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
 * 类功能:设备类型点位模板服务实现类
 *
 * 【模板覆盖写策略】
 * 1. 校验 typeId 对应的 device_types 存在且为五类产品类型
 * 2. 校验每个 pointId 在 point_definition 中存在
 * 3. 先移除该类型旧模板（逻辑删除）
 * 4. 批量插入新模板
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTypePointTemplateServiceImpl
        extends ServiceImpl<DeviceTypePointTemplateMapper, DeviceTypePointTemplate>
        implements DeviceTypePointTemplateService {

    private final DeviceTypeMapper deviceTypeMapper;
    private final PointDefinitionService pointDefinitionService;

    @Override
    public List<DeviceTypePointTemplate> listTemplates(Integer typeId) {
        return listEffectiveTemplates(typeId);
    }

    @Override
    public List<DeviceTypePointTemplate> listTemplates(Integer typeId, Long currentUserId) {
        return listEffectiveTemplates(typeId);
    }

    @Override
    public List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId, Long currentUserId) {
        return listEffectiveTemplates(typeId);
    }

    @Override
    public List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId) {
        ensureTypeExists(typeId);
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId)
                .orderByAsc(DeviceTypePointTemplate::getSortOrder);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO) {
        return saveTemplatesForType(typeId, batchDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplates(Integer typeId,
                                                        DeviceTypePointTemplateBatchDTO batchDTO,
                                                        Long currentUserId) {
        return saveTemplatesForType(typeId, batchDTO);
    }

    /*
     * 方法功能:批量覆盖保存该类型的全部模板
     * 参数:typeId-设备类型ID, batchDTO-模板项批量
     * 返回数据:List<DeviceTypePointTemplate>-保存后的模板项
     * */
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplatesForType(Integer typeId,
                                                              DeviceTypePointTemplateBatchDTO batchDTO) {
        ensureTypeExists(typeId);
        List<DeviceTypePointTemplateDTO> items = batchDTO.getItems();
        validateTemplateItems(items);

        // 1. 清空该类型旧模板（逻辑删除）
        LambdaQueryWrapper<DeviceTypePointTemplate> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(DeviceTypePointTemplate::getTypeId, typeId);
        remove(removeWrapper);

        // 2. 批量保存新模板
        List<DeviceTypePointTemplate> newTemplates = new ArrayList<>(items.size());
        for (DeviceTypePointTemplateDTO item : items) {
            DeviceTypePointTemplate template = new DeviceTypePointTemplate();
            template.setTypeId(typeId);
            template.setPointId(item.getPointId());
            template.setIsRequired(item.getIsRequired() != null ? item.getIsRequired() : 0);
            template.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            save(template);
            newTemplates.add(template);
        }
        log.info("设备类型点位模板覆盖保存完成 typeId={} count={}", typeId, newTemplates.size());
        return newTemplates;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAllTemplates(Integer typeId) {
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId);
        remove(wrapper);
        log.info("已清空设备类型 {} 的点位模板", typeId);
    }

    // ===================== 内部辅助 =====================

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
            PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(item.getPointId());
            if (seenPointIds.contains(item.getPointId())) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "模板项中点位定义ID重复: " + item.getPointId());
            }
            seenPointIds.add(item.getPointId());
        }
    }
}
