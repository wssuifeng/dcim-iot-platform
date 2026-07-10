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
import com.example.platform.service.DeviceTypePointTemplateService;
import com.example.platform.service.DevicePointMetricContract;
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
 * 1. 校验 typeId 对应的 device_types 存在
 * 2. 校验每个 pointId 在 point_definition 中存在
 * 3. 先全部移除旧模板（逻辑删除）
 * 4. 按批量插入新模板（保留 pointId/isRequired/sortOrder）
 *
 * 模板项清单中同一 pointId 不允许重复，避免覆盖写入歧义
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
        ensureTypeExists(typeId);
        LambdaQueryWrapper<DeviceTypePointTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypePointTemplate::getTypeId, typeId)
                .orderByAsc(DeviceTypePointTemplate::getSortOrder);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO) {
        ensureTypeExists(typeId);
        List<DeviceTypePointTemplateDTO> items = batchDTO.getItems();
        validateTemplateItems(items);

        // 1. 清空旧模板（逻辑删除）
        removeAllTemplates(typeId);

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
        log.info("已清空设备类型 {} 的全部点位模板", typeId);
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
