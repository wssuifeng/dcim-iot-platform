package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlarmTemplateItemCreateDTO;
import com.example.platform.dto.AlarmTemplateItemUpdateDTO;
import com.example.platform.dto.AlarmTemplateItemVO;
import com.example.platform.dto.AlarmTemplateSaveDTO;
import com.example.platform.dto.AlarmTemplateVO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.AlarmTemplateItem;

import java.util.List;

/*
 * 类功能:告警模版服务接口——模版与规则行CRUD、设备生效规则解析、删除前绑定校验
 *
 * 【绑定语义（两级）】
 *   设备.alarm_template_id（改绑，非空优先） → 设备类型.alarm_template_id（基础模版）。
 *   生效模版停用或不存在 → 视为无规则（评估跳过，不报错）。
 *
 * 【生效规则】
 *   生效模版下 enabled=1 的规则行；METRIC 按传入 pointId 过滤；
 *   通用规则（point_id 为空，如 OFFLINE）设备级全局生效。
 * */
public interface AlarmTemplateService extends IService<AlarmTemplate> {

    /*
     * 方法功能:分页查询告警模版（含引用计数：规则行数/绑定类型数/改绑设备数）
     * 参数:current-页码, size-页大小, keyword-名称或编码模糊, status-状态（可空）
     * 返回数据:Page<AlarmTemplateVO>-模版分页
     * */
    Page<AlarmTemplateVO> pageTemplates(Integer current, Integer size, String keyword, Integer status);

    /*
     * 方法功能:查询单个模版详情（含引用计数）
     * 参数:id-模版ID
     * 返回数据:AlarmTemplateVO-模版详情
     * */
    AlarmTemplateVO getTemplate(Long id);

    /*
     * 方法功能:查询模版全部规则行（含指标名称/编码/单位回填）
     * 参数:templateId-模版ID
     * 返回数据:List<AlarmTemplateItemVO>-规则行列表（按指标分组+级别排序）
     * */
    List<AlarmTemplateItemVO> listItems(Long templateId);

    /*
     * 方法功能:创建告警模版（编码全局唯一校验）
     * 参数:dto-创建请求
     * 返回数据:AlarmTemplateVO-创建后的模版
     * */
    AlarmTemplateVO createTemplate(AlarmTemplateSaveDTO dto);

    /*
     * 方法功能:更新告警模版基本信息（编码唯一校验，排除自身）
     * 参数:id-模版ID, dto-更新请求
     * 返回数据:AlarmTemplateVO-更新后的模版
     * */
    AlarmTemplateVO updateTemplate(Long id, AlarmTemplateSaveDTO dto);

    /*
     * 方法功能:删除告警模版（被设备类型或设备绑定时拒绝）
     * 参数:id-模版ID
     * 返回数据:无
     * */
    void deleteTemplate(Long id);

    /*
     * 方法功能:复制模版（自定义模版的常规起点：复制基础模版后修改）
     * 参数:id-源模版ID, newCode-新模版编码, newName-新模版名称
     * 返回数据:AlarmTemplateVO-复制出的新模版
     * */
    AlarmTemplateVO duplicateTemplate(Long id, String newCode, String newName);

    /*
     * 方法功能:为模版新增规则行（形状校验复用 METRIC/OFFLINE/COMM 契约）
     * 参数:templateId-模版ID, dto-规则行创建请求
     * 返回数据:AlarmTemplateItemVO-创建后的规则行
     * */
    AlarmTemplateItemVO createItem(Long templateId, AlarmTemplateItemCreateDTO dto);

    /*
     * 方法功能:更新模版规则行（指标与规则类型不可变更）
     * 参数:itemId-规则行ID, dto-更新请求
     * 返回数据:AlarmTemplateItemVO-更新后的规则行
     * */
    AlarmTemplateItemVO updateItem(Long itemId, AlarmTemplateItemUpdateDTO dto);

    /*
     * 方法功能:删除模版规则行
     * 参数:itemId-规则行ID
     * 返回数据:无
     * */
    void deleteItem(Long itemId);

    /*
     * 方法功能:解析设备最终生效的告警规则集（设备改绑模版→类型基础模版）
     * 参数:deviceId-设备ID, pointId-指标ID（可空=返回全部规则含通用规则）
     * 返回数据:List<PointAlarmRuleVO>-生效规则（仅启用行，含来源标记 TEMPLATE/DEVICE_OVERRIDE）
     * */
    List<PointAlarmRuleVO> findEffectiveRules(Integer deviceId, Long pointId);

    /*
     * 方法功能:解析设备的生效模版（设备改绑优先→类型基础模版）
     * 参数:device-设备实体
     * 返回数据:AlarmTemplate-生效模版；无绑定或模版不存在返回null
     * */
    AlarmTemplate resolveEffectiveTemplate(com.example.platform.entity.Device device);

    /*
     * 方法功能:更新设备类型的告警模版绑定（基础模版）
     * 参数:typeId-设备类型ID, alarmTemplateId-模版ID（可空=解绑）
     * 返回数据:无
     * */
    void updateTypeBinding(Integer typeId, Long alarmTemplateId);

    /*
     * 方法功能:更新设备的告警模版改绑（可空=跟随类型）
     * 参数:deviceId-设备ID, alarmTemplateId-模版ID（可空=跟随类型）
     * 返回数据:无
     * */
    void updateDeviceBinding(Integer deviceId, Long alarmTemplateId);
}
