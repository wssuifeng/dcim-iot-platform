package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmWebhookChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 类功能:WebhookMessageRenderer 单元测试——以各渠道官方文档报文样例为断言基准（S8-2 实现纪律）
 * 覆盖：企微/钉钉/飞书 text 报文结构、钉钉/飞书加签（URL 参数/请求体字段、时间戳单位）、
 *   ntfy 头部与优先级映射、Telegram chat_id 校验、通用 JSON 与 Authorization、业务码校验
 * */
@DisplayName("WebhookMessageRenderer 渠道报文渲染测试")
class WebhookMessageRendererTest {

    private final WebhookMessageRenderer renderer = new WebhookMessageRenderer(new tools.jackson.databind.ObjectMapper());

    private AlarmWebhookChannel channel(String type, String url, String credential) {
        AlarmWebhookChannel channel = new AlarmWebhookChannel();
        channel.setId(1L);
        channel.setName("测试渠道");
        channel.setChannelType(type);
        channel.setWebhookUrl(url);
        channel.setCredentialJson(credential);
        channel.setEnabled(1);
        channel.setMaxAlarmLevel(3);
        return channel;
    }

    private AlarmEvent event(int level) {
        AlarmEvent event = new AlarmEvent();
        event.setId(9L);
        event.setDeviceId(2);
        event.setPointId(1L);
        event.setAlarmType("METRIC");
        event.setAlarmLevel(level);
        event.setState("TRIGGERED");
        event.setAlarmMessage("高温警告：温度=45.2℃（规则 GT 35，级别2）");
        event.setTriggeredTime(LocalDateTime.of(2026, 8, 31, 10, 0));
        return event;
    }

    private JsonNode bodyJson(WebhookMessageRenderer.WebhookRequest request) throws Exception {
        return new ObjectMapper().readTree(request.body());
    }

    @Test
    @DisplayName("企业微信：msgtype/text 结构与官方文档一致，key 在 URL 无加签")
    void should_renderWecomBody() throws Exception {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_WECOM,
                        "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc", null),
                event(2), "ALARM_CREATED", "[测试] 消息");

        JsonNode body = bodyJson(request);
        assertThat(body.get("msgtype").asText()).isEqualTo("text");
        assertThat(body.get("text").get("content").asText()).isEqualTo("[测试] 消息");
        assertThat(request.headers().get("Content-Type")).isEqualTo("application/json");
        assertThat(request.url()).doesNotContain("timestamp");
    }

    @Test
    @DisplayName("钉钉：配置 secret 时毫秒时间戳与签名拼接到 URL，报文为 text 结构")
    void should_renderDingtalkSignedUrl() throws Exception {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_DINGTALK,
                        "https://oapi.dingtalk.com/robot/send?access_token=x",
                        "{\"secret\":\"SEC123\"}"),
                event(2), "ALARM_CREATED", "[测试] 消息");

        assertThat(request.body()).contains("\"msgtype\":\"text\"").contains("\"content\"");
        assertThat(request.url()).contains("&timestamp=");
        // 毫秒级时间戳（13 位数字）
        String timestamp = request.url().replaceAll(".*&timestamp=(\\d+)&sign=.*", "$1");
        assertThat(timestamp).hasSize(13);
        // 签名经 URL 编码的合法 Base64
        String sign = request.url().replaceAll(".*&sign=", "");
        assertThat(decodeBase64Lenient(java.net.URLDecoder.decode(sign,
                java.nio.charset.StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    @DisplayName("钉钉：未配置 secret 不追加签名参数")
    void should_renderDingtalkUnsigned_whenNoSecret() {
        String url = "https://oapi.dingtalk.com/robot/send?access_token=x";
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_DINGTALK, url, null),
                event(2), "ALARM_CREATED", "msg");

        assertThat(request.url()).isEqualTo(url);
    }

    @Test
    @DisplayName("飞书：秒级 timestamp + sign 放请求体（非 URL），msg_type/content 结构")
    void should_renderFeishuSignedBody() throws Exception {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_FEISHU,
                        "https://open.feishu.cn/open-apis/bot/v2/hook/xxx",
                        "{\"secret\":\"SEC456\"}"),
                event(2), "ALARM_CREATED", "[测试] 消息");

        JsonNode body = bodyJson(request);
        assertThat(request.url()).doesNotContain("sign");
        assertThat(body.has("timestamp")).isTrue();
        // 秒级时间戳（10 位）
        assertThat(body.get("timestamp").asText()).hasSize(10);
        assertThat(body.get("sign").asText()).isNotBlank();
        assertThat(body.get("msg_type").asText()).isEqualTo("text");
        assertThat(body.get("content").get("text").asText()).isEqualTo("[测试] 消息");
    }

    @Test
    @DisplayName("飞书：未配置 secret 报文不含 timestamp/sign")
    void should_renderFeishuUnsigned_whenNoSecret() throws Exception {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_FEISHU,
                        "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", null),
                event(2), "ALARM_CREATED", "msg");

        JsonNode body = bodyJson(request);
        assertThat(body.has("timestamp")).isFalse();
        assertThat(body.has("sign")).isFalse();
    }

    @Test
    @DisplayName("ntfy：纯文本消息体 + Title/Priority 头 + Bearer 认证，级别1映射 urgent")
    void should_renderNtfyHeaders() {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_NTFY,
                        "https://ntfy.sh/dcim-alerts", "{\"token\":\"tk_1\"}"),
                event(1), "ALARM_CREATED", "[测试] 消息");

        assertThat(request.headers().get("Content-Type")).isEqualTo("text/plain");
        assertThat(request.headers().get("Priority")).isEqualTo("5");
        assertThat(request.headers().get("Authorization")).isEqualTo("Bearer tk_1");
        assertThat(request.headers().get("Title")).contains("dcim 告警 L1");
        assertThat(request.body()).isEqualTo("[测试] 消息");
    }

    @Test
    @DisplayName("Telegram：缺 chat_id 报错；配置后 body 含 chat_id 与 text")
    void should_renderTelegramBody() {
        AlarmWebhookChannel noChatId = channel(AlarmWebhookChannel.TYPE_TELEGRAM,
                "https://api.telegram.org/botTOKEN/sendMessage", null);
        assertThatThrownBy(() -> renderer.render(noChatId, event(2), "ALARM_CREATED", "msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chat_id");

        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_TELEGRAM,
                        "https://api.telegram.org/botTOKEN/sendMessage",
                        "{\"chat_id\":\"@ops\"}"),
                event(2), "ALARM_CREATED", "msg");

        assertThat(request.body()).contains("\"chat_id\":\"@ops\"").contains("\"text\"");
    }

    @Test
    @DisplayName("通用渠道：平台标准 JSON + 可选 Authorization 头")
    void should_renderGenericBody() throws Exception {
        WebhookMessageRenderer.WebhookRequest request = renderer.render(
                channel(AlarmWebhookChannel.TYPE_GENERIC,
                        "https://ops.example.com/hook",
                        "{\"authHeader\":\"Bearer tk\"}"),
                event(3), "ALARM_RESEND", "msg");

        JsonNode body = bodyJson(request);
        assertThat(body.get("eventType").asText()).isEqualTo("ALARM_RESEND");
        assertThat(body.get("alarmEventId").asLong()).isEqualTo(9L);
        assertThat(body.get("alarmLevel").asInt()).isEqualTo(3);
        assertThat(request.headers().get("Authorization")).isEqualTo("Bearer tk");
    }

    @Test
    @DisplayName("业务码校验：企微/钉钉 errcode 非 0 判失败，飞书 code/StatusCode、Telegram ok")
    void should_validateChannelBusinessCode() {
        assertThat(WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_WECOM, "{\"errcode\":0,\"errmsg\":\"ok\"}")).isNotBlank();
        assertThatThrownBy(() -> WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_DINGTALK, "{\"errcode\":310000,\"errmsg\":\"keyword not in content\"}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_FEISHU, "{\"code\":0,\"msg\":\"success\"}")).isNotBlank();
        assertThatThrownBy(() -> WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_FEISHU, "{\"code\":19021}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_TELEGRAM, "{\"ok\":true}")).isNotBlank();
        assertThat(WebhookMessageRenderer.validateResponse(
                AlarmWebhookChannel.TYPE_NTFY, null)).contains("HTTP 2xx");
    }

    private boolean decodeBase64Lenient(String value) {
        try {
            return java.util.Base64.getDecoder().decode(value).length > 0;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
