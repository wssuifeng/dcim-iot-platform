package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.example.platform.common.exception.DeviceCodeAlreadyExistsException;
import com.example.platform.common.exception.DeviceNotFoundException;
import com.example.platform.entity.Device;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 设备服务实现类
 * 继承 ServiceImpl<DeviceMapper, Device> 后，自动拥有 MyBatis-Plus 提供的单表 CRUD 方法（如 list, save, update, remove 等）
 * 无需再手动编写基础的增删改查代码
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    // 关联的设备类型服务，用于跨表校验
    private final DeviceTypeService deviceTypeService;

    /**
     * 构造器注入 DeviceTypeService
     * 注意：Spring 推荐使用构造器注入，而不是 @Autowired 字段注入。
     * 这里注入的是关联服务 DeviceTypeService，绝不能把 DeviceService 自己注入自己，否则会引发循环依赖！
     */
    public DeviceServiceImpl(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    /**
     * 模糊查询设备列表
     * @param name 设备名称（模糊匹配）
     */
    @Override
    public List<Device> findByNameLike(String name) {
        // baseMapper 是 ServiceImpl 提供的属性，等价于注入的 DeviceMapper
        // 调用 Mapper XML 中自定义的 SQL 方法
        return baseMapper.findByNameLike(name);
    }

    /**
     * 查询所有在线设备
     * 演示使用 MyBatis-Plus 的 LambdaQueryWrapper 进行条件构造
     */
    @Override
    public List<Device> findOnlineDevices() {
        // 使用 LambdaQueryWrapper 构建 WHERE 条件，优点是防止列名写错，重构时自动同步字段名
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)        // WHERE status = 1 （1表示在线）
                .orderByDesc(Device::getUpdateTime); // ORDER BY update_time DESC （按更新时间倒序）

        // list() 方法由 ServiceImpl 提供，传入 wrapper 自动执行查询并返回集合
        return list(wrapper);
    }

    /**
     * 更新设备状态
     * 演示使用 MyBatis-Plus 的 LambdaUpdateWrapper 进行条件更新
     * @param id 设备ID
     * @param status 目标状态
     */
    @Override
    public boolean updateStatus(Integer id, Integer status) {
        // 使用 LambdaUpdateWrapper 构建 UPDATE 语句
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, id)       // WHERE id = ?
                .set(Device::getStatus, status); // SET status = ?

        // update() 方法由 ServiceImpl 提供，执行更新操作，返回是否成功
        return update(wrapper);
    }

    /**
     * 重写新增设备方法
     * 在执行实际保存前，增加自定义的业务校验逻辑
     */
    @Override
    public boolean save(Device device) {
        // 1. 校验设备编码唯一性（内部查询是否存在，存在则抛出异常阻断流程）
        deviceCodeExists(device.getDeviceCode());

        // 2. 校验设备类型是否存在（跨服务校验，类型ID无效则抛出异常阻断流程）
        deviceTypeService.typeIdExists(device.getTypeId());

        // 3. 校验通过后，调用父类 ServiceImpl 的 save 方法执行真正的 INSERT 操作
        return super.save(device);
    }

    /**
     * 校验设备编码是否已存在
     * @param deviceCode 设备编码
     * @throws DeviceCodeAlreadyExistsException 如果编码已存在则抛出自定义异常
     */
    public void deviceCodeExists(String deviceCode){
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode); // WHERE device_code = ?

        // count() 由 ServiceImpl 提供，查询满足条件的记录数
        if(count(wrapper) > 0){
            // 记数大于0说明编码已被占用，抛出业务异常交由全局异常处理器捕获
            throw new DeviceCodeAlreadyExistsException(deviceCode);
        }
    }

    /**
     * 根据设备类型ID查询设备列表
     * @param typeId 设备类型ID
     */
    public List<Device> findByTypeId(Integer typeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getTypeId, typeId); // WHERE type_id = ?
        return list(wrapper);
    }

    /**
     * 按状态分组统计设备数量
     * @return 返回 List<Map<String, Object>>，通常 Map 中包含 status 和 count 两个字段
     */
    public List<Map<String, Object>> countByStatus() {
        // 调用 Mapper XML 中自定义的统计 SQL（通常是 GROUP BY status）
        return baseMapper.countByStatus();
    }
    /**
     * 设备存在性检查
     * @return 返回 Device
     */
    public Device getDeviceOrThrow(Integer id) {
        Device device = getById(id);
        if (device == null) {
            throw new DeviceNotFoundException(id);
        }
        return device;
    }

}
