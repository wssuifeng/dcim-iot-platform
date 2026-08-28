package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.AlarmTemplateItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/*
 * 类功能:告警模版规则行数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface AlarmTemplateItemMapper extends BaseMapper<AlarmTemplateItem> {

    /*
     * 方法功能:查询全部启用模版下的启用 OFFLINE 规则行（离线检测粗筛最小阈值用）
     * 参数:无
     * 返回数据:List<AlarmTemplateItem>-启用模版中 rule_type=OFFLINE 且 enabled=1 的规则行
     * */
    @Select("SELECT i.* FROM alarm_template_item i " +
            "JOIN alarm_template t ON t.id = i.template_id " +
            "WHERE i.rule_type = 'OFFLINE' AND i.enabled = 1 AND t.status = 0")
    List<AlarmTemplateItem> selectEnabledOfflineItems();
}
