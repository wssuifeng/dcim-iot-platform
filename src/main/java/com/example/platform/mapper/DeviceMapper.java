package com.example.platform.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备Mapper接口
 * 继承BaseMapper后，自动拥有以下方法：
 * - insert(entity)：插入
 * - deleteById(id)：根据ID删除
 * - updateById(entity)：根据ID更新
 * - selectById(id)：根据ID查询
 * - selectList(wrapper)：条件查询列表
 * - selectPage(page, wrapper)：分页查询
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 根据设备名称模糊查询
     * @param name 设备名称
     * @return 设备列表
     */
    @Select("SELECT * FROM devices WHERE device_name LIKE CONCAT('%', #{name}, '%')")
    List<Device> findByNameLike(@Param("name") String name);

    /**
     * 统计各状态的设备数量
     * @return 统计结果
     */
    @Select("SELECT status, COUNT(*) as count FROM devices GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();
}