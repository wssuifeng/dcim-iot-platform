package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.DeviceCodeAlreadyExistsException;
import com.example.platform.common.exception.DeviceNotFoundException;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.Device;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备服务实现类，负责设备的CRUD、状态管理、编码唯一性校验及告警配置更新
 * */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final DeviceTypeService deviceTypeService;
    private final AlertConfigService alertConfigService;

    public DeviceServiceImpl(DeviceTypeService deviceTypeService, AlertConfigService alertConfigService) {
        this.deviceTypeService = deviceTypeService;
        this.alertConfigService = alertConfigService;
    }

    /*
     * 方法功能:根据设备名称模糊查询设备列表
     * 参数:name-设备名称关键词
     * 返回数据:List<Device>-匹配的设备列表
     * */
    @Override
    public List<Device> findByNameLike(String name) {
        return baseMapper.findByNameLike(name);
    }

    /*
     * 方法功能:查询所有在线设备（status=1），按更新时间倒序排列
     * 参数:无
     * 返回数据:List<Device>-在线设备列表
     * */
    @Override
    public List<Device> findOnlineDevices() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)
                .orderByDesc(Device::getUpdateTime);
        return list(wrapper);
    }

    /*
     * 方法功能:更新指定设备的状态
     * 参数:id-设备ID, status-目标状态值
     * 返回数据:boolean-更新是否成功
     * */
    @Override
    public boolean updateStatus(Integer id, Integer status) {
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, id)
                .set(Device::getStatus, status);
        return update(wrapper);
    }

    /*
     * 方法功能:新增设备，保存前校验编码唯一性和设备类型有效性
     * 参数:device-待新增的设备对象
     * 返回数据:boolean-保存是否成功
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean save(Device device) {
        // 1. 校验设备编码唯一性
        deviceCodeExists(device.getDeviceCode());

        // 2. 校验设备类型是否存在
        deviceTypeService.getTypeOrThrow(device.getTypeId());

        // 3. 校验通过后执行INSERT
        return super.save(device);
    }

    /*
     * 方法功能:校验设备编码是否已存在，存在则抛出异常
     * 参数:deviceCode-设备编码
     * 返回数据:无
     * */
    public void deviceCodeExists(String deviceCode){
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);

        if(count(wrapper) > 0){
            throw new DeviceCodeAlreadyExistsException(deviceCode);
        }
    }

    /*
     * 方法功能:根据设备类型ID查询设备列表
     * 参数:typeId-设备类型ID
     * 返回数据:List<Device>-该类型下的设备列表
     * */
    @Override
    public List<Device> findByTypeId(Integer typeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getTypeId, typeId);
        return list(wrapper);
    }

    /*
     * 方法功能:按状态分组统计设备数量
     * 参数:无
     * 返回数据:List<Map<String, Object>>-每个Map包含status和count两个字段
     * */
    @Override
    public List<Map<String, Object>> countByStatus() {
        return baseMapper.countByStatus();
    }

    /*
     * 方法功能:设备存在性检查，不存在则抛出DeviceNotFoundException
     * 参数:id-设备ID
     * 返回数据:Device-查询到的设备对象
     * */
    @Override
    public Device getDeviceOrThrow(Integer id) {
        Device device = getById(id);
        if (device == null) {
            throw new DeviceNotFoundException(id);
        }
        return device;
    }

    /*
     * 方法功能:更新指定设备的告警规则配置
     * 参数:id-设备ID, alertRuleConfig-告警规则配置对象
     * 返回数据:无
     * */
    @Override
    public void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig) {
        Device device = getDeviceOrThrow(id);
        device.setAlertRuleConfig(alertRuleConfig);
        this.updateById(device);
        alertConfigService.evictDeviceAlertConfig(id);
    }

    /*
     * 方法功能:查询最后在线时间早于指定阈值的所有设备（用于离线检测）
     * 参数:checkTime-判定阈值时间
     * 返回数据:List<Device>-离线设备列表
     * */
    @Override
    public List<Device> getOnlineDetection(LocalDateTime checkTime) {
        return baseMapper.getOnlineDetection(checkTime);
    }

    /*
     * 方法功能:按可选状态和名称条件分页查询设备，按更新时间倒序排列
     * 参数:current-当前页码, size-每页大小, status-设备状态（可选）, name-设备名称（可选，模糊匹配）
     * 返回数据:Page<Device>-分页设备数据
     * */
    @Override
    public Page<Device> pageByCondition(Integer current, Integer size, Integer status, String name) {
        Page<Device> page = new Page<>(current, size);
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
        }
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Device::getDeviceName, name);
        }
        wrapper.orderByDesc(Device::getUpdateTime);
        return page(page, wrapper);
    }

    /*
     * 方法功能:更新设备最后在线时间并设置状态为在线
     * 参数:deviceId-设备ID
     * 返回数据:无
     * */
    @Override
    public void updateLastOnlineTime(Integer deviceId) {
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, deviceId)
                .set(Device::getLastOnlineTime, LocalDateTime.now())
                .set(Device::getStatus, 1);
        update(wrapper);
    }
}
