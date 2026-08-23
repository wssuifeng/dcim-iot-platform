package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DeviceType;

import java.util.List;

/*
 * 类功能:设备类型服务接口，定义设备类型的CRUD、存在性校验及告警配置更新等业务方法
 * */
public interface DeviceTypeService extends IService<DeviceType> {

    /*
     * 方法功能:设备类型存在性检查，不存在则抛出DeviceTypeNotFound
     * 参数:id-设备类型ID
     * 返回数据:DeviceType-查询到的设备类型对象
     * */
    DeviceType getTypeOrThrow(Integer id);

    /*
     * 方法功能:根据类型编码查询设备类型
     * 参数:typeCode-类型编码
     * 返回数据:DeviceType-查询到的设备类型对象，未找到返回null
     * */
    DeviceType findByTypeCode(String typeCode);

    /*
     * 方法功能:查询所有设备类型列表，按更新时间倒序排列
     * 参数:无
     * 返回数据:List<DeviceType>-全部设备类型列表
     * */
    List<DeviceType> listAll();

    /*
     * 方法功能:创建新设备类型，校验类型编码唯一性
     * 参数:deviceType-待创建的设备类型对象
     * 返回数据:DeviceType-创建成功的设备类型对象
     * */
    DeviceType create(DeviceType deviceType);

    /*
     * 方法功能:更新设备类型信息（支持部分字段更新）
     * 参数:id-设备类型ID, deviceType-待更新的设备类型对象
     * 返回数据:DeviceType-更新后的设备类型对象
     * */
    DeviceType updateById(Integer id, DeviceType deviceType);

    /*
     * 方法功能:根据ID删除设备类型，存在关联设备时拒绝删除
     * 参数:id-设备类型ID
     * 返回数据:无
     * */
    void deleteById(Integer id);
}
