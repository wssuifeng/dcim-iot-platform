package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.PointAlarmRuleCreateDTO;
import com.example.platform.dto.PointAlarmRuleUpdateDTO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.DevicePointAlarmRule;

import java.util.List;

/*
 * 类功能:点位告警规则服务接口，提供规则CRUD与设备生效规则的三级降级解析
 *
 * 【降级语义（D18，按点位替换式）】
 *   某点位存在设备级覆盖规则 → 该点位整体生效设备级规则、忽略模板级；
 *   无设备级 → 生效组织模板规则；再无 → 生效平台模板规则。
 *   通用规则（OFFLINE/COMM，pointId为空）按同样语义以空点位键分组。
 *
 * 【规则归属】
 *   METRIC 规则的 pointId 一律取模板指标项的 pointId（服务端填充，不允许任意点位）；
 *   规则组织归属冗余自模板项（平台级模板=0，组织模板=组织ID；设备覆盖规则=设备所属组织）。
 * */
public interface DevicePointAlarmRuleService extends IService<DevicePointAlarmRule> {

    /*
     * 方法功能:分页查询规则（按数据范围过滤）
     * 参数:currentUserId-当前用户, current/size-分页, typeId-设备类型, deviceId-设备, pointId-点位, ruleType-规则类型, enabled-启用状态
     * 返回数据:Page<PointAlarmRuleVO>-规则分页（含点位名称回填）
     * */
    Page<PointAlarmRuleVO> pageRules(Long currentUserId, Integer current, Integer size,
                                     Integer typeId, Integer deviceId, Long pointId,
                                     String ruleType, Integer enabled);

    /*
     * 方法功能:创建告警规则（校验模板项归属/点位在模板内/类型-参数组合/业务唯一键）
     * 参数:dto-创建请求, currentUserId-当前用户
     * 返回数据:PointAlarmRuleVO-创建后的规则
     * */
    PointAlarmRuleVO create(PointAlarmRuleCreateDTO dto, Long currentUserId);

    /*
     * 方法功能:更新告警规则（模板指标项与规则类型不可变更）
     * 参数:id-规则ID, dto-更新请求, currentUserId-当前用户
     * 返回数据:PointAlarmRuleVO-更新后的规则
     * */
    PointAlarmRuleVO update(Long id, PointAlarmRuleUpdateDTO dto, Long currentUserId);

    /*
     * 方法功能:删除告警规则（逻辑删除，仅ORG_ADMIN）
     * 参数:id-规则ID, currentUserId-当前用户
     * 返回数据:无
     * */
    void delete(Long id, Long currentUserId);

    /*
     * 方法功能:启用/停用规则
     * 参数:id-规则ID, enabled-目标状态, currentUserId-当前用户
     * 返回数据:无
     * */
    void updateStatus(Long id, boolean enabled, Long currentUserId);

    /*
     * 方法功能:解析设备最终生效的规则集（设备级覆盖→组织模板→平台模板，按点位替换式）
     * 参数:deviceId-设备ID, pointId-点位ID（可空=返回全部点位规则）
     * 返回数据:List<PointAlarmRuleVO>-生效规则（仅enabled=1，含source层级标记）
     * */
    List<PointAlarmRuleVO> findEffectiveRules(Integer deviceId, Long pointId);
}
