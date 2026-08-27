package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DevicePointBindingDTO;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointBindingMapper;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DeviceTypePointTemplateService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * 类功能:设备点位绑定服务实现类
 *
 * 【角色与范围】
 *   - 写操作权限由控制器 RBAC（ADMIN/OPERATOR）保证，Service 层校验设备存在性
 *   - 查询：任何已认证用户按 deviceId 查询绑定关系
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePointBindingServiceImpl
        extends ServiceImpl<DevicePointBindingMapper, DevicePointBinding>
        implements DevicePointBindingService {

    private final DeviceScopeHelper deviceScopeHelper;
    private final PointDefinitionService pointDefinitionService;
    private final DeviceTypePointTemplateService deviceTypePointTemplateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DevicePointBinding bindPoint(DevicePointBindingDTO dto, Long currentUserId) {
        deviceScopeHelper.ensureDeviceAccess(dto.getDeviceId(), currentUserId);
        pointDefinitionService.getPointDefinitionOrThrow(dto.getPointId());

        LambdaQueryWrapper<DevicePointBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DevicePointBinding::getDeviceId, dto.getDeviceId())
                .eq(DevicePointBinding::getPointId, dto.getPointId());
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备 " + dto.getDeviceId() + " 已绑定点位 " + dto.getPointId());
        }

        DevicePointBinding binding = new DevicePointBinding();
        binding.setDeviceId(dto.getDeviceId());
        binding.setPointId(dto.getPointId());
        binding.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        if (!save(binding)) {
            throw new BusinessException("设备点位绑定失败");
        }
        log.info("设备点位绑定成功 deviceId={} pointId={}", dto.getDeviceId(), dto.getPointId());
        return binding;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DevicePointBinding updateStatus(Long bindingId, Integer status, Long currentUserId) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许 0-启用 1-停用");
        }
        DevicePointBinding binding = getBindingOrThrow(bindingId);
        deviceScopeHelper.ensureDeviceAccess(binding.getDeviceId(), currentUserId);
        binding.setStatus(status);
        if (!updateById(binding)) {
            throw new BusinessException("更新绑定状态失败");
        }
        return binding;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindPoint(Long bindingId, Long currentUserId) {
        DevicePointBinding binding = getBindingOrThrow(bindingId);
        deviceScopeHelper.ensureDeviceAccess(binding.getDeviceId(), currentUserId);
        if (!removeById(bindingId)) {
            throw new BusinessException("移除设备点位绑定失败");
        }
        log.info("设备点位绑定移除 bindingId={} deviceId={}", bindingId, binding.getDeviceId());
    }

    @Override
    public List<DevicePointBinding> listByDevice(Integer deviceId, Long currentUserId) {
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        LambdaQueryWrapper<DevicePointBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DevicePointBinding::getDeviceId, deviceId)
                .orderByAsc(DevicePointBinding::getCreateTime);
        return list(wrapper);
    }

    @Override
    public DevicePointBinding getActiveBindingOrThrow(Integer deviceId, Long pointId, Long currentUserId) {
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        LambdaQueryWrapper<DevicePointBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DevicePointBinding::getDeviceId, deviceId)
                .eq(DevicePointBinding::getPointId, pointId)
                .eq(DevicePointBinding::getStatus, 0);
        DevicePointBinding binding = getOne(wrapper, false);
        if (binding == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备 " + deviceId + " 未启用点位 " + pointId + " 的绑定");
        }
        return binding;
    }

    @Override
    public Page<DevicePointBinding> pageByConditions(Long currentUserId, Integer current, Integer size,
                                                     Integer deviceId, Long pointId, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        Page<DevicePointBinding> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<DevicePointBinding> wrapper = new LambdaQueryWrapper<>();

        if (deviceId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备点位绑定查询必须指定 deviceId");
        }
        deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        wrapper.eq(DevicePointBinding::getDeviceId, deviceId);
        if (pointId != null) {
            wrapper.eq(DevicePointBinding::getPointId, pointId);
        }
        if (status != null) {
            wrapper.eq(DevicePointBinding::getStatus, status);
        }
        wrapper.orderByDesc(DevicePointBinding::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DevicePointBinding> initBindingsFromTemplate(Integer deviceId, Integer typeId, Long currentUserId) {
        Device device = deviceScopeHelper.ensureDeviceAccess(deviceId, currentUserId);
        if (device.getTypeId() == null || !device.getTypeId().equals(typeId)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备 " + deviceId + " 的类型与传入 typeId 不匹配");
        }

        List<DeviceTypePointTemplate> templates = deviceTypePointTemplateService.listEffectiveTemplates(
                typeId, currentUserId);
        if (templates.isEmpty()) {
            log.info("设备类型 {} 未配置点位模板，跳过模板拷贝", typeId);
            return new ArrayList<>();
        }

        // 已绑定点位ID集合
        LambdaQueryWrapper<DevicePointBinding> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(DevicePointBinding::getDeviceId, deviceId);
        Set<Long> existPointIds = list(existWrapper).stream()
                .map(DevicePointBinding::getPointId)
                .collect(Collectors.toSet());

        List<DevicePointBinding> result = new ArrayList<>();
        for (DeviceTypePointTemplate template : templates) {
            if (existPointIds.contains(template.getPointId())) {
                continue; // 已绑定，跳过
            }
            PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(template.getPointId());
            DevicePointBinding binding = new DevicePointBinding();
            binding.setDeviceId(deviceId);
            binding.setPointId(point.getId());
            binding.setStatus(0);
            save(binding);
            result.add(binding);
        }
        log.info("设备点位模板拷贝完成 deviceId={} typeId={} 新增绑定数={}",
                deviceId, typeId, result.size());
        return result;
    }

    private DevicePointBinding getBindingOrThrow(Long id) {
        DevicePointBinding binding = getById(id);
        if (binding == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "设备点位绑定记录不存在: " + id);
        }
        return binding;
    }
}
