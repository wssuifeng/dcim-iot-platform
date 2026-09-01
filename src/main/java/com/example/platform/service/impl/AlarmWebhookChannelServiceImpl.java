package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.AlarmWebhookChannelSaveDTO;
import com.example.platform.dto.AlarmWebhookChannelVO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmWebhookChannel;
import com.example.platform.mapper.AlarmWebhookChannelMapper;
import com.example.platform.service.AlarmWebhookChannelService;
import com.example.platform.service.WebhookSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/*
 * 类功能:告警 webhook 渠道管理服务实现（ADMIN-only：CRUD + 测试发送）
 *
 * 【测试发送语义】渲染标注"[测试]"的固定样例报文直接发送一次（不重试）——
 *   不产生告警事件、不入告警分发链、不写通知日志；用于用户保存配置后即时验证连通性。
 *   渠道业务码校验与真实分发同口径（errcode=0 等）。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmWebhookChannelServiceImpl implements AlarmWebhookChannelService {

    private final AlarmWebhookChannelMapper channelMapper;
    private final WebhookMessageRenderer renderer;
    private final WebhookSender webhookSender;
    private final com.example.platform.ingest.wecom.WecomAibotClient wecomAibotClient;
    private final com.example.platform.ingest.wecom.WecomAibotChatRegistry aibotChatRegistry;

    @Override
    public List<AlarmWebhookChannelVO> listChannels() {
        return channelMapper.selectList(new LambdaQueryWrapper<AlarmWebhookChannel>()
                        .orderByAsc(AlarmWebhookChannel::getId)).stream()
                .map(AlarmWebhookChannelVO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AlarmWebhookChannelVO createChannel(AlarmWebhookChannelSaveDTO dto) {
        checkNameUnique(dto.getName(), null);
        AlarmWebhookChannel channel = new AlarmWebhookChannel();
        apply(channel, dto);
        if (channelMapper.insert(channel) <= 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "创建渠道失败");
        }
        connectAibotIfEnabled(channel);
        log.info("webhook 渠道创建 channelId={} name={} type={}",
                channel.getId(), channel.getName(), channel.getChannelType());
        return AlarmWebhookChannelVO.fromEntity(channel);
    }

    @Override
    public AlarmWebhookChannelVO updateChannel(Long id, AlarmWebhookChannelSaveDTO dto) {
        AlarmWebhookChannel channel = getChannelOrThrow(id);
        String oldBotId = null;
        if (isAibot(channel)) {
            try {
                oldBotId = com.example.platform.ingest.wecom.WecomAibotCredential
                        .parse(channel.getCredentialJson()).botId();
            } catch (Exception ignored) {
            }
        }
        checkNameUnique(dto.getName(), id);
        apply(channel, dto);
        if (channelMapper.updateById(channel) <= 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "更新渠道失败");
        }
        // aibot 渠道：凭证/启停可能变化，重连收敛到最新配置
        if (isAibot(channel)) {
            wecomAibotClient.disconnect(oldBotId);
            connectAibotIfEnabled(channel);
        }
        log.info("webhook 渠道更新 channelId={} name={} type={}",
                channel.getId(), channel.getName(), channel.getChannelType());
        return AlarmWebhookChannelVO.fromEntity(channel);
    }

    @Override
    public void deleteChannel(Long id) {
        AlarmWebhookChannel channel = getChannelOrThrow(id);
        if (isAibot(channel)) {
            wecomAibotClient.disconnect(oldAibotBotId(id));
        }
        channelMapper.deleteById(id);
        log.info("webhook 渠道删除 channelId={}", id);
    }

    private boolean isAibot(AlarmWebhookChannel channel) {
        return AlarmWebhookChannel.TYPE_WECOM_AIBOT.equals(channel.getChannelType());
    }

    // 启用的 aibot 渠道建立长连接；凭证不全时跳过（保存允许先占位，发送时报错可见）
    private void connectAibotIfEnabled(AlarmWebhookChannel channel) {
        if (!isAibot(channel) || channel.getEnabled() == null || channel.getEnabled() != 1) {
            return;
        }
        try {
            com.example.platform.ingest.wecom.WecomAibotCredential cred =
                    com.example.platform.ingest.wecom.WecomAibotCredential.parse(channel.getCredentialJson());
            wecomAibotClient.connect(cred.botId(), cred.secret());
        } catch (Exception ex) {
            log.warn("企微智能机器人长连接未建立（发送时将重试） channelId={} error={}",
                    channel.getId(), ex.getMessage());
        }
    }

    // 更新场景：用库里旧凭证的 botId 断开（凭证可能已改）
    private String oldAibotBotId(Long channelId) {
        try {
            AlarmWebhookChannel before = channelMapper.selectById(channelId);
            if (before != null) {
                return com.example.platform.ingest.wecom.WecomAibotCredential
                        .parse(before.getCredentialJson()).botId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public String connectAibotForCapture(String botId, String secret) {
        if (botId == null || botId.isBlank() || secret == null || secret.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "botId 与 secret 不能为空");
        }
        wecomAibotClient.connect(botId.trim(), secret.trim());
        return "长连接已建立——现在在群里 @机器人说一句话，然后点\"捕获会话\"";
    }

    /*
     * 方法功能:列出企微智能机器人已捕获的会话（用户在群里 @机器人后出现，绑定 chatid 用）
     * 参数:botId-机器人ID
     * 返回数据:会话列表（chatid/类型/最近触发者/时间）
     * */
    @Override
    public java.util.List<com.example.platform.ingest.wecom.WecomAibotChatRegistry.ChatEntry>
    listAibotChats(String botId) {
        return aibotChatRegistry.listByBot(botId);
    }

    @Override
    public String testSend(Long id) {
        AlarmWebhookChannel channel = getChannelOrThrow(id);
        // 固定样例事件（不落库）：级别取渠道最高推送级别，覆盖渲染分支
        AlarmEvent sampleEvent = new AlarmEvent();
        sampleEvent.setId(0L);
        sampleEvent.setAlarmType("TEST");
        sampleEvent.setAlarmLevel(channel.getMaxAlarmLevel() != null ? channel.getMaxAlarmLevel() : 3);
        sampleEvent.setState("TRIGGERED");
        sampleEvent.setAlarmMessage("[测试] 渠道连通性样例消息");
        sampleEvent.setTriggeredTime(java.time.LocalDateTime.now());

        String text = "[测试] dcim 告警通道连通性测试（" + channel.getName() + "）";
        if (isAibot(channel)) {
            try {
                com.example.platform.ingest.wecom.WecomAibotCredential cred =
                        com.example.platform.ingest.wecom.WecomAibotCredential.parse(channel.getCredentialJson());
                wecomAibotClient.sendMarkdown(cred.botId(), cred.secret(), cred.chatid(),
                        "> **[测试] dcim 告警通道连通性测试**" + "\n\n" + text);
                return "已通过长连接推送（errcode=0）";
            } catch (Exception ex) {
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                        "测试发送失败: " + ex.getMessage());
            }
        }
        try {
            WebhookMessageRenderer.WebhookRequest request =
                    renderer.render(channel, sampleEvent, "TEST", text);
            String response = webhookSender.send(request.url(), request.headers(), request.body());
            String outcome = WebhookMessageRenderer.validateResponse(channel.getChannelType(), response);
            log.info("webhook 渠道测试发送成功 channelId={}", id);
            return outcome;
        } catch (Exception ex) {
            log.warn("webhook 渠道测试发送失败 channelId={} error={}", id, ex.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                    "测试发送失败: " + ex.getMessage());
        }
    }

    private void apply(AlarmWebhookChannel channel, AlarmWebhookChannelSaveDTO dto) {
        channel.setName(dto.getName().trim());
        channel.setChannelType(dto.getChannelType());
        channel.setWebhookUrl(dto.getWebhookUrl().trim());
        channel.setCredentialJson(dto.getCredentialJson());
        channel.setMaxAlarmLevel(dto.getMaxAlarmLevel() != null ? dto.getMaxAlarmLevel() : 3);
        channel.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<AlarmWebhookChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmWebhookChannel::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(AlarmWebhookChannel::getId, excludeId);
        }
        if (channelMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.ALARM_TEMPLATE_CODE_EXISTS, "渠道名称已存在: " + name);
        }
    }

    private AlarmWebhookChannel getChannelOrThrow(Long id) {
        AlarmWebhookChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "渠道不存在: " + id);
        }
        return channel;
    }
}
