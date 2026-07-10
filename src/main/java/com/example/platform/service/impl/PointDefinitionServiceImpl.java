package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.PointDefinitionMapper;
import com.example.platform.service.PointDefinitionService;
import com.example.platform.service.DevicePointMetricContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/*
 * 类功能:点位定义服务实现类，维护全局共享的点位字典
 * */
@Slf4j
@Service
public class PointDefinitionServiceImpl extends ServiceImpl<PointDefinitionMapper, PointDefinition> implements PointDefinitionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointDefinition createPointDefinition(PointDefinitionCreateDTO createDTO) {
        checkPointCodeUnique(createDTO.getPointCode());
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
        if (!save(point)) {
            throw new BusinessException("创建点位定义失败，请稍后重试");
        }
        log.info("点位定义创建成功 pointCode={} pointName={}",
                point.getPointCode(), point.getPointName());
        return point;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointDefinition updatePointDefinition(Long id, PointDefinitionUpdateDTO updateDTO) {
        PointDefinition point = getById(id);
        if (point == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "点位定义不存在: " + id);
        }
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
    public PointDefinition getPointDefinitionOrThrow(Long id) {
        PointDefinition point = getById(id);
        if (point == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "点位定义不存在: " + id);
        }
        return point;
    }

    @Override
    public Page<PointDefinition> pagePointDefinitions(Integer current, Integer size, String keyword, String dataType) {
        int[] params = PaginationUtils.validate(current, size);
        Page<PointDefinition> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PointDefinition::getPointCode, keyword.trim())
                    .or().like(PointDefinition::getPointName, keyword.trim());
        }
        if (StringUtils.hasText(dataType)) {
            wrapper.eq(PointDefinition::getDataType, dataType.trim());
        }
        wrapper.orderByDesc(PointDefinition::getCreateTime);
        return page(page, wrapper);
    }

    private void checkPointCodeUnique(String pointCode) {
        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointDefinition::getPointCode, pointCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "点位编码已存在: " + pointCode);
        }
    }
}
