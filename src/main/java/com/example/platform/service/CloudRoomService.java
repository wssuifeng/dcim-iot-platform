package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.entity.CloudRoom;

/*
 * 类功能:云机房服务接口，定义机房顶层资源的生命周期管理方法
 * */
public interface CloudRoomService extends IService<CloudRoom> {

    /*
     * 方法功能:创建机房（校验编码全局唯一）
     * 参数:createDTO-机房创建请求, currentUserId-当前操作用户ID
     * 返回数据:CloudRoom-创建后的机房
     * */
    CloudRoom createCloudRoom(CloudRoomCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:更新机房基本信息
     * 参数:id-机房ID, updateDTO-机房更新请求, currentUserId-当前操作用户ID
     * 返回数据:更新后的 CloudRoom
     * */
    CloudRoom updateCloudRoom(Long id, CloudRoomUpdateDTO updateDTO, Long currentUserId);

    /*
     * 方法功能:启用/停用机房
     * 参数:id-机房ID, status-目标状态(0正常 1停用), currentUserId-当前操作用户ID
     * 返回数据:更新后的 CloudRoom
     * */
    CloudRoom updateStatus(Long id, Integer status, Long currentUserId);

    /*
     * 方法功能:按 ID 查询机房，不存在抛出资源不存在异常
     * 参数:id-机房ID, currentUserId-当前操作用户ID
     * 返回数据:CloudRoom
     * */
    CloudRoom getCloudRoomOrThrow(Long id, Long currentUserId);

    /*
     * 方法功能:分页查询机房
     * 参数:currentUserId-当前操作用户ID, current-页码, size-页大小,
     *      keyword-名称关键字(可选), status-状态筛选(可选)
     * 返回数据:Page<CloudRoom>-机房分页
     * */
    Page<CloudRoom> pageCloudRooms(Long currentUserId, Integer current, Integer size,
                                   String keyword, Integer status);

    /*
     * 方法功能:按机房编码精确查询
     * 参数:roomCode-机房编码
     * 返回数据:CloudRoom-匹配的机房，不存在返回null
     * */
    CloudRoom findByRoomCode(String roomCode);
}
