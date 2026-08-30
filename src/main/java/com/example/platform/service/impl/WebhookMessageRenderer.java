package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmWebhookChannel;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/*
 * 类功能:webhook 渠道报文渲染器——按渠道类型产出（URL, Headers, Body）
 *
 * 【实现纪律：以各渠道官方文档为准，禁止凭印象编造】核对记录（2026-08-31）：
 *
 *   企业微信群机器人（developer.work.weixin.qq.com/document/path/91770，页尾标注最后更新 2025/08/07）：
 *     - URL：https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<KEY>（key 即唯一凭证，无加签机制）
 *     - 报文：{"msgtype":"text","text":{"content":"..."}}，content ≤2048 字节 UTF-8
 *     - 频率：每机器人 ≤20 条/分钟
 *
 *   钉钉自定义机器人（open.dingtalk.com/document/robots/custom-robot-access 及安全设置文档）：
 *     - 报文：{"msgtype":"text","text":{"content":"..."}}（安全设置选关键词时 content 须含关键词）
 *     - 加签：secret 与毫秒级 timestamp 拼接为 stringToSign=timestamp+"\n"+secret，
 *       以其为 Key 对空字节串 HmacSHA256 后 Base64，URLEncoder.encode 后拼到 URL
 *       &timestamp=<毫秒>&sign=<签名>（官方"安全设置"文档算法描述；真实机器人联调即最终校验）
 *     - 频率：每机器人 ≤20 条/分钟
 *     - 【重要事实】2023-09-01 起钉钉停建新自定义机器人（存量可用）；新接入走
 *       "企业内部应用机器人"，其 /robot/send?access_token= 的报文与加签方案与本渲染器一致
 *
 *   飞书自定义机器人（open.feishu.cn/document/client-docs/bot-v3/add-custom-bot）：
 *     - 报文：{"msg_type":"text","content":{"text":"..."}}；请求体 ≤20KB
 *     - 加签：sign = Base64(HmacSHA256(key = timestamp+"\n"+secret, data = 空串))，
 *       timestamp 为秒级且须在 1 小时内；timestamp/sign 放【请求体】而非 URL
 *     - 频率：100 次/分钟、5 次/秒（避开整点/半点，超限报 11232）
 *
 *   ntfy（docs.ntfy.sh/publish/）：直接 POST 纯文本即消息体；Title/Priority 走 HTTP 头；
 *     Authorization: Bearer <token> 或 Basic 认证可选。
 *
 *   Telegram Bot API（core.telegram.org/api#bot-api）：POST https://api.telegram.org/bot<TOKEN>/sendMessage，
 *     报文 {"chat_id":...,"text":"..."}；token 在 URL 内。
 *
 *   GENERIC：POST 平台标准 JSON（无渠道专有格式），可选 Authorization 头。
 * */
@Component
@RequiredArgsConstructor
public class WebhookMessageRenderer {

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_TEXT = "text/plain";

    private final ObjectMapper objectMapper;

    /**
     * 渲染结果
     */
    public record WebhookRequest(String url, Map<String, String> headers, String body) {
    }

    /*
     * 方法功能:按渠道渲染告警 webhook 请求
     * 参数:channel-渠道配置, event-告警事件, eventName-WS 事件类型（ALARM_CREATED/UPGRADED/RESEND/RECOVERED）, text-已组装的纯文本消息
     * 返回数据:WebhookRequest（url + headers + body）
     * */
    public WebhookRequest render(AlarmWebhookChannel channel, AlarmEvent event,
                                 String eventName, String text) {
        JsonNode credential = parseCredential(channel.getCredentialJson());
        switch (channel.getChannelType()) {
            case AlarmWebhookChannel.TYPE_WECOM:
                return renderWecom(channel, text);
            case AlarmWebhookChannel.TYPE_DINGTALK:
                return renderDingtalk(channel, text, credential);
            case AlarmWebhookChannel.TYPE_FEISHU:
                return renderFeishu(channel, text, credential);
            case AlarmWebhookChannel.TYPE_NTFY:
                return renderNtfy(channel, event, text, credential);
            case AlarmWebhookChannel.TYPE_TELEGRAM:
                return renderTelegram(channel, text, credential);
            case AlarmWebhookChannel.TYPE_GENERIC:
                return renderGeneric(channel, event, eventName, text, credential);
            default:
                throw new IllegalArgumentException("未知渠道类型: " + channel.getChannelType());
        }
    }

    /*
     * 方法功能:渠道业务码校验——HTTP 200 不代表成功（如钉钉关键词不匹配返回 errcode≠0）
     * 参数:channelType-渠道类型, response-响应体
     * 返回数据:成功时的响应摘要
     * 抛出异常:业务码非成功时抛 IllegalStateException
     * */
    public static String validateResponse(String channelType, String response) {
        if (response == null || response.trim().isEmpty()) {
            return "HTTP 2xx（无响应体）";
        }
        String body = response.trim();
        switch (channelType) {
            case AlarmWebhookChannel.TYPE_WECOM:
            case AlarmWebhookChannel.TYPE_DINGTALK:
                if (!body.contains("\"errcode\":0")) {
                    throw new IllegalStateException("errcode 非 0: " + shorten(body));
                }
                break;
            case AlarmWebhookChannel.TYPE_FEISHU:
                if (!body.contains("\"code\":0") && !body.contains("\"StatusCode\":0")) {
                    throw new IllegalStateException("飞书业务码非 0: " + shorten(body));
                }
                break;
            case AlarmWebhookChannel.TYPE_TELEGRAM:
                if (!body.contains("\"ok\":true")) {
                    throw new IllegalStateException("Telegram ok≠true: " + shorten(body));
                }
                break;
            // NTFY / GENERIC：HTTP 2xx 即成功
            default:
                break;
        }
        return shorten(body);
    }

    private static String shorten(String body) {
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    // 企业微信：key 在 URL 内，文本 ≤2048 字节，无加签
    private WebhookRequest renderWecom(AlarmWebhookChannel channel, String text) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("msgtype", "text");
        body.putObject("text").put("content", truncate(text, 2000));
        return new WebhookRequest(channel.getWebhookUrl(),
                Map.of("Content-Type", CONTENT_TYPE_JSON), body.toString());
    }

    // 钉钉：配置了 secret 则毫秒时间戳加签拼 URL；关键词安全设置依赖用户文案自含
    private WebhookRequest renderDingtalk(AlarmWebhookChannel channel, String text, JsonNode credential) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("msgtype", "text");
        body.putObject("text").put("content", truncate(text, 2000));
        String url = channel.getWebhookUrl();
        String secret = textValue(credential, "secret");
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            String sign = hmacSha256Base64(stringToSign);
            String connector = url.contains("?") ? "&" : "?";
            url = url + connector + "timestamp=" + timestamp
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
        }
        return new WebhookRequest(url,
                Map.of("Content-Type", CONTENT_TYPE_JSON), body.toString());
    }

    // 飞书：秒级 timestamp + sign 放请求体（非 URL）
    private WebhookRequest renderFeishu(AlarmWebhookChannel channel, String text, JsonNode credential) {
        ObjectNode body = objectMapper.createObjectNode();
        String secret = textValue(credential, "secret");
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis() / 1000;
            String stringToSign = timestamp + "\n" + secret;
            String sign = hmacSha256Base64(stringToSign);
            body.put("timestamp", String.valueOf(timestamp));
            body.put("sign", sign);
        }
        body.put("msg_type", "text");
        body.putObject("content").put("text", truncate(text, 18000));
        return new WebhookRequest(channel.getWebhookUrl(),
                Map.of("Content-Type", CONTENT_TYPE_JSON), body.toString());
    }

    // ntfy：纯文本消息体 + Title/Priority 头；token 可选
    private WebhookRequest renderNtfy(AlarmWebhookChannel channel, AlarmEvent event,
                                      String text, JsonNode credential) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", CONTENT_TYPE_TEXT);
        headers.put("Title", truncate("dcim 告警 " + eventAlarmLevelTag(event), 80));
        headers.put("Priority", String.valueOf(ntfyPriority(event.getAlarmLevel())));
        String token = textValue(credential, "token");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return new WebhookRequest(channel.getWebhookUrl(), headers, truncate(text, 4000));
    }

    // Telegram：chat_id 必须配置在凭证 JSON
    private WebhookRequest renderTelegram(AlarmWebhookChannel channel, String text, JsonNode credential) {
        String chatId = textValue(credential, "chat_id");
        if (chatId == null || chatId.isEmpty()) {
            throw new IllegalArgumentException("Telegram 渠道必须在凭证 JSON 中配置 chat_id");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", truncate(text, 4000));
        return new WebhookRequest(channel.getWebhookUrl(),
                Map.of("Content-Type", CONTENT_TYPE_JSON), body.toString());
    }

    // 通用：平台标准 JSON 结构 + 可选 Authorization 头
    private WebhookRequest renderGeneric(AlarmWebhookChannel channel, AlarmEvent event,
                                         String eventName, String text, JsonNode credential) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("eventType", eventName);
        body.put("alarmEventId", event.getId());
        body.put("alarmType", event.getAlarmType());
        body.put("alarmLevel", event.getAlarmLevel());
        body.put("state", event.getState());
        body.put("message", event.getAlarmMessage());
        body.put("deviceId", event.getDeviceId());
        body.put("pointId", event.getPointId());
        body.put("triggeredTime", event.getTriggeredTime() != null
                ? event.getTriggeredTime().toString() : null);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        String authHeader = textValue(credential, "authHeader");
        if (authHeader != null && !authHeader.isEmpty()) {
            headers.put("Authorization", authHeader);
        }
        return new WebhookRequest(channel.getWebhookUrl(), headers, body.toString());
    }

    // HmacSHA256(key=stringToSign, data=空串) → Base64（钉钉/飞书同构签名算法）
    private String hmacSha256Base64(String stringToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(new byte[0]);
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception ex) {
            throw new IllegalStateException("加签计算失败: " + ex.getMessage(), ex);
        }
    }

    // 告警级别 → ntfy 优先级映射（级别1紧急 → ntfy 5 urgent；5提示 → 1 min）
    private int ntfyPriority(Integer alarmLevel) {
        if (alarmLevel == null) {
            return 3;
        }
        return switch (alarmLevel) {
            case 1 -> 5;
            case 2 -> 4;
            case 3 -> 3;
            case 4 -> 2;
            default -> 1;
        };
    }

    private String eventAlarmLevelTag(AlarmEvent event) {
        return "L" + (event.getAlarmLevel() != null ? event.getAlarmLevel() : "-");
    }

    // UTF-8 字节截断（企业微信 content 2048 字节等场景），避免切出半字符
    private String truncate(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int safeBytes = maxBytes;
        while (safeBytes > 0 && (bytes[safeBytes] & 0xC0) == 0x80) {
            safeBytes--;
        }
        return new String(bytes, 0, safeBytes, StandardCharsets.UTF_8);
    }

    private JsonNode parseCredential(String credentialJson) {
        if (credentialJson == null || credentialJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(credentialJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("凭证 JSON 解析失败: " + ex.getMessage(), ex);
        }
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
