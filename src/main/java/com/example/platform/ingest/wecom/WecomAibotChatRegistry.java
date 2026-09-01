package com.example.platform.ingest.wecom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 类功能:企微智能机器人 chatid 捕获登记簿（内存态）
 *
 * 【为什么】官方协议（path/101463）规定：群聊 chatid 只能从回调事件中获取，
 *   无主动查询接口——机器人收到群里 @消息时才有 chatid。本登记簿把
 *   aibot_msg_callback / aibot_event_callback 中出现过的会话记下来，
 *   供渠道页选择绑定（用户 @机器人一句即完成登记）。
 * 【内存态取舍】重启丢失可接受（群不变，用户再 @一句即恢复；也可从
 *   平台日志回溯）。后续需要持久化再落表。
 * */
@Slf4j
@Component
public class WecomAibotChatRegistry {

    public record ChatEntry(String chatid, String chatType, String lastUser,
                            String lastContent, java.time.LocalDateTime seenAt) {
    }

    // botId -> (chatid -> entry)
    private final Map<String, Map<String, ChatEntry>> registry = new ConcurrentHashMap<>();

    /*
     * 方法功能:登记一次回调中出现的会话
     * 参数:botId-机器人ID, chatid-会话ID, chatType-single/group, userid-触发者, content-消息摘要
     * 返回数据:无
     * */
    public void record(String botId, String chatid, String chatType, String userid, String content) {
        if (botId == null || chatid == null || chatid.isBlank()) {
            return;
        }
        registry.computeIfAbsent(botId, k -> new ConcurrentHashMap<>())
                .put(chatid, new ChatEntry(chatid, chatType, userid,
                        content != null && content.length() > 50 ? content.substring(0, 50) : content,
                        java.time.LocalDateTime.now()));
        log.info("企微智能机器人会话登记 botId={} chatid={} type={} from={}", botId, chatid, chatType, userid);
    }

    /*
     * 方法功能:列出某机器人捕获到的全部会话（渠道页绑定选择用）
     * 参数:botId-机器人ID
     * 返回数据:会话列表（按最近活跃排序）
     * */
    public java.util.List<ChatEntry> listByBot(String botId) {
        Map<String, ChatEntry> chats = registry.get(botId);
        if (chats == null || chats.isEmpty()) {
            return java.util.List.of();
        }
        return chats.values().stream()
                .sorted(java.util.Comparator.comparing(ChatEntry::seenAt).reversed())
                .toList();
    }

    /*
     * 方法功能:校验 chatid 是否已被该机器人捕获（绑定前防呆）
     * 参数:botId-机器人ID, chatid-会话ID
     * 返回数据:boolean-是否捕获过
     * */
    public boolean known(String botId, String chatid) {
        Map<String, ChatEntry> chats = registry.get(botId);
        return chats != null && chats.containsKey(chatid);
    }
}
