package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.entity.DeviceTypePointTemplate;

import java.util.List;

/*
 * 类功能:设备类型点位模板服务接口
 *
 * 【生效解析】按设备类型查询生效模板项（按排序）。
 * */
public interface DeviceTypePointTemplateService extends IService<DeviceTypePointTemplate> {

    /*
     * 方法功能:按设备类型ID查询生效模板
     * 参数:typeId-设备类型ID
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项（按排序）
     * */
    List<DeviceTypePointTemplate> listTemplates(Integer typeId);

    /*
     * 方法功能:按当前用户查询生效模板
     * 参数:typeId-设备类型ID, currentUserId-当前用户
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项
     * */
    List<DeviceTypePointTemplate> listTemplates(Integer typeId, Long currentUserId);

    /*
     * 方法功能:生效模板解析（设备绑定初始化/规则降级解析用）
     * 参数:typeId-设备类型ID
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项
     * */
    List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId);

    /*
     * 方法功能:按用户解析生效模板
     * 参数:typeId-设备类型ID, currentUserId-当前用户
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项
     * */
    List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId, Long currentUserId);

    /*
     * 方法功能:批量覆盖保存模板
     * 参数:typeId-设备类型ID, batchDTO-模板项批量
     * 返回数据:List<DeviceTypePointTemplate>-保存后的模板项
     * */
    List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO);

    /*
     * 方法功能:按当前用户覆盖保存模板
     * 参数:typeId-设备类型ID, batchDTO-模板项批量, currentUserId-当前用户
     * 返回数据:List<DeviceTypePointTemplate>-保存后的模板项
     * */
    List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO,
                                                Long currentUserId);

    /*
     * 方法功能:删除该类型的全部模板（覆盖写前清空）
     * 参数:typeId-设备类型ID
     * 返回数据:无
     * */
    void removeAllTemplates(Integer typeId);
}
