package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.entity.DeviceTypePointTemplate;

import java.util.List;

/*
 * 类功能:设备类型点位模板服务接口
 *
 * 【角色与范围】
 *   - PLATFORM_ADMIN：可批量设置某类型的默认点位模板（覆盖写）
 *   - ORG_ADMIN/OPERATOR/VIEWER：只读查询某类型的默认点位
 *
 * 用途：创建设备时以模板初始化设备的点位绑定
 * */
public interface DeviceTypePointTemplateService extends IService<DeviceTypePointTemplate> {

    /*
     * 方法功能:按设备类型ID查询模板（含点位明细）
     * */
    List<DeviceTypePointTemplate> listTemplates(Integer typeId);

    /*
     * 方法功能:批量覆盖设置某设备类型的点位模板
     * 流程：先清空原模板 → 按items插入新模板
     * */
    List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO);

    /*
     * 方法功能:删除某设备类型的全部模板
     * */
    void removeAllTemplates(Integer typeId);
}