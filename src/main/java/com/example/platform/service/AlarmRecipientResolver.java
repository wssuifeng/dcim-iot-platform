package com.example.platform.service;

import java.util.Set;

/*
 * 类功能:告警接收人解析接口，解析可接收告警通知的用户集合
 * */
public interface AlarmRecipientResolver {

    /*
     * 方法功能:解析可接收告警通知的用户ID集合
     * 返回数据:Set<Long>-启用状态且具备接收角色的用户ID集合，无匹配时返回空集
     * */
    Set<Long> resolveRecipientUserIds();
}
