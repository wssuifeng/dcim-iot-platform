package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.dto.PointResourceExportPackage;
import com.example.platform.entity.PointDefinition;

import java.util.List;

/*
 * 类功能:点位定义服务接口（双层：平台级全局 + 组织私有，D07）
 *
 * 【归属规则】organization_id=0 为平台级全局共享；>0 为组织私有。
 *   - 创建：组织上下文存在 → 组织私有；平台管理员无组织头 → 平台级
 *   - 可见性：组织用户 = 平台级(0) + 本组织；平台管理员 = 全部
 *   - 写权限：平台级仅 PLATFORM_ADMIN；组织级本组织 ORG_ADMIN/OPERATOR
 *   - 编码唯一性：(organization_id, point_code) 组织内唯一
 * */
public interface PointDefinitionService extends IService<PointDefinition> {

    /*
     * 方法功能:创建点位定义（归属=当前组织上下文；平台管理员无组织头则建平台级）
     * 参数:createDTO-创建请求, currentUserId-当前用户
     * 返回数据:PointDefinition-创建后的点位
     * */
    PointDefinition createPointDefinition(PointDefinitionCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:更新点位定义（校验归属写权限：平台级仅平台管理员，组织级本组织）
     * 参数:id-点位ID, updateDTO-更新请求, currentUserId-当前用户
     * 返回数据:PointDefinition-更新后的点位
     * */
    PointDefinition updatePointDefinition(Long id, PointDefinitionUpdateDTO updateDTO, Long currentUserId);

    /*
     * 方法功能:删除点位定义（被模板/绑定/规则引用时拒绝，错误码4035）
     * 参数:id-点位ID, currentUserId-当前用户
     * 返回数据:无
     * */
    void deletePointDefinition(Long id, Long currentUserId);

    /*
     * 方法功能:查询单个点位（不存在抛业务异常）
     * 参数:id-点位ID
     * 返回数据:PointDefinition-点位实体
     * */
    PointDefinition getPointDefinitionOrThrow(Long id);

    /*
     * 方法功能:分页查询点位（组织用户=平台级+本组织；平台管理员=全部）
     * 参数:currentUserId-当前用户, current/size-分页, keyword-编码/名称关键字, dataType-数据类型
     * 返回数据:Page<PointDefinition>-点位分页
     * */
    Page<PointDefinition> pagePointDefinitions(Long currentUserId, Integer current, Integer size,
                                               String keyword, String dataType);

    /*
     * 方法功能:查询可绑点位下拉（平台级+指定组织的私有点位）
     * 参数:organizationId-组织ID（可空=仅平台级）
     * 返回数据:List<PointDefinition>-可选点位列表
     * */
    List<PointDefinition> listAvailablePoints(Long organizationId);

    /*
     * 方法功能:导出组织的私有点位+模板为传输包（JSON结构，按编码关联）
     * 参数:organizationId-组织ID
     * 返回数据:PointResourceExportPackage-导出包
     * */
    PointResourceExportPackage exportOrgPackage(Long organizationId);

    /*
     * 方法功能:导入资源包为目标组织的私有副本（编码冲突自动重映射）
     * 参数:pkg-导入包, targetOrganizationId-目标组织ID, currentUserId-操作人
     * 返回数据:无
     * */
    void importPackage(PointResourceExportPackage pkg, Long targetOrganizationId, Long currentUserId);
}
