package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.Device;

import java.util.List;


/**
 * 设备服务接口
 * 继承 IService<Device>，自动获得以下通用方法（无需重写声明）：
 * 【查询】
 * - getById(id)                根据主键查询
 * - listByIds(ids)             根据 ID 集合批量查询
 * - listByMap(columnMap)       根据字段条件查询
 * - getOne(wrapper)            查询单条（默认要求唯一）
 * - list(wrapper)              根据条件查询列表
 * - count(wrapper)             统计记录数
 * - page(page, wrapper)        分页查询
 * - listObjs(wrapper)          查询单列值列表
 * - listMaps(wrapper)          查询结果转为 Map 列表
 * 【新增/更新】
 * - save(entity)               插入一条
 * - saveBatch(list)            批量插入
 * - saveOrUpdate(entity)       有主键则更新，无则插入
 * - updateById(entity)         根据主键更新
 * - update(entity, wrapper)    根据条件更新
 * - updateBatchById(list)      根据 ID 批量更新
 * 【删除】
 * - removeById(id)             根据主键删除
 * - removeByMap(columnMap)     根据条件删除
 * - remove(wrapper)            根据条件构造器删除
 * - removeBatchByIds(ids)      根据 ID 批量删除
 * 【链式操作】
 * - query()                    返回链式查询对象，可 .eq().list()
 * - update()                   返回链式更新对象，可 .set().update()
 * @author your-name
 */
public interface DeviceService extends IService<Device> {
    /**
     * 设备存在性检查
     */

    Device getDeviceOrThrow(Integer id);
    /**
     * 根据名称模糊查询
     */
    List<Device> findByNameLike(String name);

    /**
     * 查询在线设备
     */
    List<Device> findOnlineDevices();

    /**
     * 更新设备状态
     */
    boolean updateStatus(Integer id, Integer status);
}
