package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;

/*
 * 类功能:点位定义服务接口，维护全局点位字典
 *
 * 【角色与范围】
 *   - PLATFORM_ADMIN：可读写（新建/更新/删除）
 *   - ORG_ADMIN/OPERATOR/VIEWER：只读（点位字典是全局共享元数据）
 * */
public interface PointDefinitionService extends IService<PointDefinition> {

    PointDefinition createPointDefinition(PointDefinitionCreateDTO createDTO);

    PointDefinition updatePointDefinition(Long id, PointDefinitionUpdateDTO updateDTO);

    PointDefinition getPointDefinitionOrThrow(Long id);

    Page<PointDefinition> pagePointDefinitions(Integer current, Integer size, String keyword, String dataType);
}