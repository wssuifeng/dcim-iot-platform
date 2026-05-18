package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据访问接口，提供设备的模糊查询、状态统计及离线检测等自定义方法
 * */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /*
     * 方法功能:根据设备名称模糊查询设备列表
     * 参数:name-设备名称关键词
     * 返回数据:List<Device>-匹配的设备列表
     * */
    List<Device> findByNameLike(@Param("name") String name);

    /*
     * 方法功能:按状态分组统计设备数量
     * 参数:无
     * 返回数据:List<Map<String, Object>>-每个Map包含status和count两个字段
     * */
    List<Map<String, Object>> countByStatus();

    /*
     * 方法功能:查询最后在线时间早于指定阈值的设备（用于离线检测）
     * 参数:checkTime-判定阈值时间
     * 返回数据:List<Device>-离线设备列表
     * */
    List<Device> getOnlineDetection(@Param("checkTime") LocalDateTime checkTime);
}
