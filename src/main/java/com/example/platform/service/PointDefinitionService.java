package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.dto.PointResourceExportPackage;
import com.example.platform.entity.PointDefinition;

import java.util.List;

/*
 * 类功能:指标定义服务接口
 *
 * 【归属规则】指标全局共享；编码全局唯一。
 * */
public interface PointDefinitionService extends IService<PointDefinition> {

    /*
     * 方法功能:创建指标定义（编码全局唯一）
     * 参数:createDTO-创建请求, currentUserId-当前用户
     * 返回数据:PointDefinition-创建后的指标
     * */
    PointDefinition createPointDefinition(PointDefinitionCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:创建指标定义（编码全局唯一）
     * 参数:createDTO-创建请求, currentUserId-当前用户
     * 返回数据:PointDefinition-创建后的指标
     * */
    PointDefinition createPlatformPointDefinition(PointDefinitionCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:更新指标定义
     * 参数:id-指标ID, updateDTO-更新请求, currentUserId-当前用户
     * 返回数据:PointDefinition-更新后的指标
     * */
    PointDefinition updatePointDefinition(Long id, PointDefinitionUpdateDTO updateDTO, Long currentUserId);

    /*
     * 方法功能:删除指标定义（被模板/绑定/规则引用时拒绝，错误码4035）
     * 参数:id-指标ID, currentUserId-当前用户
     * 返回数据:无
     * */
    void deletePointDefinition(Long id, Long currentUserId);

    /*
     * 方法功能:查询单个指标（不存在抛业务异常）
     * 参数:id-指标ID
     * 返回数据:PointDefinition-指标实体
     * */
    PointDefinition getPointDefinitionOrThrow(Long id);

    /*
     * 方法功能:查询单个指标（不存在抛业务异常）
     * 参数:id-指标ID, currentUserId-当前用户
     * 返回数据:PointDefinition-指标实体
     * */
    PointDefinition getPointDefinitionOrThrow(Long id, Long currentUserId);

    /*
     * 方法功能:分页查询指标
     * 参数:currentUserId-当前用户, current/size-分页, keyword-编码/名称关键字, dataType-数据类型
     * 返回数据:Page<PointDefinition>-指标分页
     * */
    Page<PointDefinition> pagePointDefinitions(Long currentUserId, Integer current, Integer size,
                                               String keyword, String dataType);

    /*
     * 方法功能:查询可绑指标下拉
     * 参数:无
     * 返回数据:List<PointDefinition>-可选指标列表
     * */
    List<PointDefinition> listAvailablePoints();

    /*
     * 方法功能:查询可绑指标下拉
     * 参数:currentUserId-当前用户
     * 返回数据:List<PointDefinition>-可选指标列表
     * */
    List<PointDefinition> listAvailablePoints(Long currentUserId);

    /*
     * 方法功能:导出全部指标+模板为传输包（JSON结构，按编码关联）
     * 参数:无
     * 返回数据:PointResourceExportPackage-导出包
     * */
    PointResourceExportPackage exportPackage();

    /*
     * 方法功能:导入指标资源包（编码冲突复用已有，模板覆盖写）
     * 参数:pkg-导入包, currentUserId-操作人
     * 返回数据:无
     * */
    void importPackage(PointResourceExportPackage pkg, Long currentUserId);
}
