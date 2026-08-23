package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.entity.DeviceTypePointTemplate;

import java.util.List;

/*
 * 类功能:设备类型点位模板服务接口（双层：平台级默认 + 组织自定义，D07）
 *
 * 【生效解析】组织级模板（organization_id=设备所属组织）存在则整体生效组织级；
 *   无则降级平台级模板（organization_id=0）。模板粒度=（组织,类型）整体。
 *
 * 【组织上下文】listTemplates/saveTemplates 使用当前组织上下文
 *   （X-Organization-Id 头）：组织用户操作组织级；无组织头视为平台级。
 * */
public interface DeviceTypePointTemplateService extends IService<DeviceTypePointTemplate> {

    /*
     * 方法功能:按设备类型ID查询生效模板（当前组织上下文：组织级优先降级平台级）
     * 参数:typeId-设备类型ID
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项（按排序）
     * */
    List<DeviceTypePointTemplate> listTemplates(Integer typeId);

    /*
     * 方法功能:显式组织参数的生效模板解析（设备绑定初始化/规则降级解析用）
     * 参数:typeId-设备类型ID, organizationId-组织ID（可空=仅平台级）
     * 返回数据:List<DeviceTypePointTemplate>-生效模板项
     * */
    List<DeviceTypePointTemplate> listEffectiveTemplates(Integer typeId, Long organizationId);

    /*
     * 方法功能:批量覆盖保存模板（目标层=当前组织上下文，无组织头为平台级）
     * 参数:typeId-设备类型ID, batchDTO-模板项批量
     * 返回数据:List<DeviceTypePointTemplate>-保存后的模板项
     * */
    List<DeviceTypePointTemplate> saveTemplates(Integer typeId, DeviceTypePointTemplateBatchDTO batchDTO);

    /*
     * 方法功能:显式目标组织的模板覆盖保存（导入/平台管理用）
     * 参数:typeId-设备类型ID, batchDTO-模板项批量, organizationId-目标组织（0=平台级）
     * 返回数据:List<DeviceTypePointTemplate>-保存后的模板项
     * */
    List<DeviceTypePointTemplate> saveTemplatesForOrganization(Integer typeId,
                                                               DeviceTypePointTemplateBatchDTO batchDTO,
                                                               Long organizationId);

    /*
     * 方法功能:删除当前组织上下文层的全部模板（同组织同类型的覆盖写前清空）
     * 参数:typeId-设备类型ID
     * 返回数据:无
     * */
    void removeAllTemplates(Integer typeId);
}
