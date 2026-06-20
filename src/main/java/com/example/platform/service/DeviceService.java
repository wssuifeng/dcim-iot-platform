package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.dto.DeviceImportResult;
import com.example.platform.entity.Device;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备服务接口，定义设备的查询、状态管理、告警配置及离线检测等业务方法
 * */
public interface DeviceService extends IService<Device> {

    /*
     * 方法功能:设备存在性检查，不存在则抛出异常
     * 参数:id-设备ID
     * 返回数据:Device-查询到的设备对象
     * */
    Device getDeviceOrThrow(Integer id);

    /*
     * 方法功能:根据设备名称模糊查询
     * 参数:name-设备名称关键词
     * 返回数据:List<Device>-匹配的设备列表
     * */
    List<Device> findByNameLike(String name);

    /*
     * 方法功能:查询所有在线设备
     * 参数:无
     * 返回数据:List<Device>-在线设备列表
     * */
    List<Device> findOnlineDevices();

    /*
     * 方法功能:更新设备状态
     * 参数:id-设备ID, status-目标状态值
     * 返回数据:boolean-更新是否成功
     * */
    boolean updateStatus(Integer id, Integer status);

    /*
     * 方法功能:更新指定设备的告警规则配置
     * 参数:id-设备ID, alertRuleConfig-告警规则配置对象
     * 返回数据:无
     * */
    void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig);

    /*
     * 方法功能:查询最后在线时间早于指定阈值的设备（用于离线检测）
     * 参数:checkTime-判定阈值时间
     * 返回数据:List<Device>-离线设备列表
     * */
    List<Device> getOnlineDetection(LocalDateTime checkTime);

    /*
     * 方法功能:按可选状态和名称条件分页查询设备
     * 参数:current-当前页码, size-每页大小, status-设备状态（可选）, name-设备名称（可选）
     * 返回数据:Page<Device>-分页设备数据
     * */
    Page<Device> pageByCondition(Integer current, Integer size, Integer status, String name);

    /*
     * 方法功能:按可选状态和名称条件查询全部设备（用于导出）
     * 参数:status-设备状态（可选）, name-设备名称（可选）
     * 返回数据:List<Device>-设备列表
     * */
    List<Device> listByCondition(Integer status, String name);

    /*
     * 方法功能:根据设备类型ID查询设备列表
     * 参数:typeId-设备类型ID
     * 返回数据:List<Device>-该类型下的设备列表
     * */
    List<Device> findByTypeId(Integer typeId);

    /*
     * 方法功能:按状态分组统计设备数量
     * 参数:无
     * 返回数据:List<Map<String, Object>>-每个Map包含status和count两个字段
     * */
    List<Map<String, Object>> countByStatus();

    void updateLastOnlineTime(Integer deviceId);

    /*
     * 方法功能:从 Excel 文件批量导入设备
     * 参数:file-Excel 文件
     * 返回数据:DeviceImportResult-导入结果（成功数/失败数/失败明细）
     * */
    DeviceImportResult importFromExcel(MultipartFile file);
}
