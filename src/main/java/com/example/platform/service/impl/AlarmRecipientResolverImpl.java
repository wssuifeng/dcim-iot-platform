package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.AlarmRecipientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * 类功能:告警接收人解析服务实现，单租户下广播给全部启用状态的运维角色用户
 *
 * 【收束说明】多组织时期按组织/机房数据范围过滤接收人；单租户收束后
 *   实例即边界，告警通知全部启用状态的 ADMIN/OPERATOR/VIEWER 用户。
 * VIEWER 保留接收资格：只读值班人员同样需要被告警触达。
 * */
@Service
@RequiredArgsConstructor
public class AlarmRecipientResolverImpl implements AlarmRecipientResolver {

    private static final int USER_STATUS_ENABLED = 0; // 用户启用状态
    private static final Set<String> RECIPIENT_ROLES = Set.of( // 可接收告警的角色
            PlatformRole.ADMIN,
            PlatformRole.OPERATOR,
            PlatformRole.VIEWER);

    private final SysUserMapper sysUserMapper;

    /*
     * 方法功能:解析可接收告警通知的用户ID集合
     * 返回数据:Set<Long>-启用状态且角色匹配的用户ID集合，无匹配时返回空集
     * */
    @Override
    public Set<Long> resolveRecipientUserIds() {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getStatus, USER_STATUS_ENABLED)
                .in(SysUser::getRole, RECIPIENT_ROLES);
        List<SysUser> users = sysUserMapper.selectList(query);

        Set<Long> recipientUserIds = new LinkedHashSet<>();
        for (SysUser user : users) {
            if (user.getId() != null && RECIPIENT_ROLES.contains(user.getRole())) {
                recipientUserIds.add(user.getId());
            }
        }
        return recipientUserIds;
    }
}
