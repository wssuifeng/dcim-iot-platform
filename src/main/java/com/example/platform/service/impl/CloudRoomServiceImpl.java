package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.CloudRoomMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.OrganizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/*
 * 类功能:云机房服务实现类，负责机房 CRUD、编码唯一性校验与服务层数据范围校验
 *
 * 【范围过滤策略】
 *   - PLATFORM_ADMIN：scopeType=PLATFORM，但 contract 约定 "不默认穿透组织业务数据"，
 *     故分页查询返回空集（PLATFORM_ADMIN 不业务穿透）；按 ID 读单条仍允许（用于平台运维场景）
 *   - ORG_ADMIN：按 context.organizationIds 过滤，可查询本组织所有机房；可对本组织机房 CRUD
 *   - OPERATOR：按 context.cloudRoomIds 限定到授权机房；不可创建/修改/删除机房
 *   - VIEWER：同 OPERATOR 的查询范围；不可写
 *
 * 【业务约束】
 *   - roomCode 组织内唯一
 *   - status 仅允许 0(正常)/1(停用)
 *   - 创建/更新机房时必须校验 organizationId 落在当前用户范围内
 * */
@Slf4j
@Service
public class CloudRoomServiceImpl extends ServiceImpl<CloudRoomMapper, CloudRoom> implements CloudRoomService {

    private final DataScopeService dataScopeService;
    private final OrganizationService organizationService;

    public CloudRoomServiceImpl(DataScopeService dataScopeService,
                                OrganizationService organizationService) {
        this.dataScopeService = dataScopeService;
        this.organizationService = organizationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom createCloudRoom(CloudRoomCreateDTO createDTO, Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        // 仅 ORG_ADMIN 可创建机房；OPERATOR/VIEWER 无写权限；PLATFORM_ADMIN 默认不业务穿透
        ensureCanManageCloudRoom(context, createDTO.getOrganizationId());

        // 校验目标组织存在且为正常状态，避免悬挂归属
        Organization ownerOrg = organizationService.getById(createDTO.getOrganizationId());
        if (ownerOrg == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "归属组织不存在: " + createDTO.getOrganizationId());
        }

        checkRoomCodeUniqueInOrg(createDTO.getOrganizationId(), createDTO.getRoomCode());

        CloudRoom cloudRoom = new CloudRoom();
        cloudRoom.setOrganizationId(createDTO.getOrganizationId());
        cloudRoom.setRoomName(createDTO.getRoomName());
        cloudRoom.setRoomCode(createDTO.getRoomCode());
        cloudRoom.setLocation(createDTO.getLocation());
        cloudRoom.setStatus(0);
        if (!save(cloudRoom)) {
            throw new BusinessException("创建机房失败，请稍后重试");
        }
        log.info("机房创建成功 orgId={} roomCode={} roomName={}",
                cloudRoom.getOrganizationId(), cloudRoom.getRoomCode(), cloudRoom.getRoomName());
        return cloudRoom;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom updateCloudRoom(Long id, CloudRoomUpdateDTO updateDTO, Long currentUserId) {
        CloudRoom cloudRoom = getCloudRoomOrThrow(id, currentUserId);

        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!isOrgAdminOfOrg(context, cloudRoom.getOrganizationId())) {
            // 非 ORG_ADMIN 不可更新机房基础信息
            throw new DataScopeViolationException("CloudRoom", String.valueOf(id));
        }

        if (updateDTO.getRoomName() != null) {
            cloudRoom.setRoomName(updateDTO.getRoomName());
        }
        if (updateDTO.getLocation() != null) {
            cloudRoom.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getStatus() != null) {
            validateStatus(updateDTO.getStatus());
            cloudRoom.setStatus(updateDTO.getStatus());
        }
        if (!updateById(cloudRoom)) {
            throw new BusinessException("更新机房失败，数据可能已被他人修改");
        }
        return cloudRoom;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom updateStatus(Long id, Integer status, Long currentUserId) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
        CloudRoom cloudRoom = getCloudRoomOrThrow(id, currentUserId);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!isOrgAdminOfOrg(context, cloudRoom.getOrganizationId())) {
            throw new DataScopeViolationException("CloudRoom", String.valueOf(id));
        }
        cloudRoom.setStatus(status);
        if (!updateById(cloudRoom)) {
            throw new BusinessException("更新机房状态失败");
        }
        return cloudRoom;
    }

    @Override
    public CloudRoom getCloudRoomOrThrow(Long id, Long currentUserId) {
        CloudRoom cloudRoom = getById(id);
        if (cloudRoom == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "机房不存在: " + id);
        }
        ensureCloudRoomScope(currentUserId, cloudRoom);
        return cloudRoom;
    }

    @Override
    public Page<CloudRoom> pageCloudRooms(Long currentUserId, Integer current, Integer size,
                                          Long organizationId, String keyword, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        Page<CloudRoom> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<CloudRoom> wrapper = new LambdaQueryWrapper<>();

        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        if (context.isPlatformAdmin()) {
            // 平台管理员默认不穿透组织业务：分页查询返回空集
            return new Page<>(params[0], params[1]);
        }

        Set<Long> orgIds = context.getOrganizationIds();
        if (orgIds.isEmpty()) {
            return new Page<>(params[0], params[1]);
        }
        wrapper.in(CloudRoom::getOrganizationId, orgIds);

        // 若请求指定 organizationId，进一步细化到该组织（必须在用户范围内）
        if (organizationId != null) {
            if (!orgIds.contains(organizationId)) {
                // 拒绝跨组织查询
                return new Page<>(params[0], params[1]);
            }
            wrapper.eq(CloudRoom::getOrganizationId, organizationId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(CloudRoom::getRoomName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(CloudRoom::getStatus, status);
        }
        // OPERATOR/READ_ONLY 范围限定到具体授权机房（ROOMS 这种细化）
        if ("ROOMS".equals(context.getScopeType()) && !context.getCloudRoomIds().isEmpty()) {
            wrapper.in(CloudRoom::getId, context.getCloudRoomIds());
        }
        wrapper.orderByDesc(CloudRoom::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public CloudRoom findByOrganizationAndCode(Long organizationId, String roomCode) {
        if (organizationId == null || roomCode == null || roomCode.isBlank()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<CloudRoom>()
                .eq(CloudRoom::getOrganizationId, organizationId)
                .eq(CloudRoom::getRoomCode, roomCode.trim()), false);
    }

    /*
     * 方法功能:校验用户是否有权管理目标组织下的机房（用于创建/更新场景）
     * 规则:仅 ORG_ADMIN 且该组织在其授权范围内方可；其他角色一律拒绝
     * */
    private void ensureCanManageCloudRoom(DataScopeContext context, Long organizationId) {
        if (!context.isOrgAdmin()) {
            throw new DataScopeViolationException("CloudRoom", "create-in-org:" + organizationId);
        }
        if (!context.hasOrganizationAccess(organizationId)) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }
    }

    private boolean isOrgAdminOfOrg(DataScopeContext context, Long organizationId) {
        return context.isOrgAdmin() && context.hasOrganizationAccess(organizationId);
    }

    /*
     * 方法功能:校验用户是否有权访问目标机房（用于读取/操作前）
     * 规则:
     *   - PLATFORM_ADMIN：可选访问任意单机房（运维场景），不构成默认穿透
     *   - ORG_ADMIN：机房组织归属在 context.organizationIds 内通过
     *   - OPERATOR/VIEWER：机房 ID 在 context.cloudRoomIds 内通过
     * */
    private void ensureCloudRoomScope(Long currentUserId, CloudRoom cloudRoom) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (context.isPlatformAdmin()) {
            return;
        }
        if ("ROOMS".equals(context.getScopeType())) {
            // ROOMS 范围严格按授权机房集合核对，机房不在集合内一律拒绝
            if (context.getCloudRoomIds() == null
                    || !context.getCloudRoomIds().contains(cloudRoom.getId())) {
                throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoom.getId()));
            }
            return;
        }
        if (!context.hasOrganizationAccess(cloudRoom.getOrganizationId())) {
            throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoom.getId()));
        }
    }

    private void checkRoomCodeUniqueInOrg(Long organizationId, String roomCode) {
        LambdaQueryWrapper<CloudRoom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudRoom::getOrganizationId, organizationId)
                .eq(CloudRoom::getRoomCode, roomCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "机房编码在组织内已存在: " + roomCode);
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
    }
}
