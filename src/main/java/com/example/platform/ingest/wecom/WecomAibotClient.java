package com.example.platform.ingest.wecom;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.ResultCode;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/*
 * 类功能:企微智能机器人长连接客户端（S8-2 增补，协议：官方文档 path/101463，2026/05/25 版）
 *
 * 【连接管理】每 botId 一条 wss://openws.work.weixin.qq.com 长连接（官方：每机器人
 *   仅允许一条，新连接踢旧连接——同一 botId 重复连接由服务端踢旧，天然防重复）。
 *   订阅（aibot_subscribe 携带 bot_id+secret）→ 30s ping 心跳 → 断线自动重连（10s 退避）。
 * 【发送】aibot_send_msg（markdown）到指定 chatid；同步等待响应（req_id 关联，5s 超时）。
 * 【回调接收】aibot_msg_callback / aibot_event_callback 中的 chatid 登记到 ChatRegistry
 *   （群聊 chatid 无法主动查询，只能从回调捕获——用户在群里 @机器人即登记）。
 * 【协议纪律】报文 cmd/headers/body 结构与 errcode=0 校验均按官方文档实现，禁止臆造。
 * */
@Slf4j
@Component
public class WecomAibotClient {

    private static final String WSS_URL = "wss://openws.work.weixin.qq.com";
    private static final int HEARTBEAT_SECONDS = 30;
    private static final int RECONNECT_DELAY_SECONDS = 10;
    private static final int SEND_TIMEOUT_SECONDS = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WecomAibotChatRegistry chatRegistry;

    // botId -> 活动连接
    private final ConcurrentHashMap<String, AibotConnection> connections = new ConcurrentHashMap<>();
    // req_id -> 等待中的响应
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public WecomAibotClient(WecomAibotChatRegistry chatRegistry) {
        this.chatRegistry = chatRegistry;
    }

    /*
     * 方法功能:建立（或复用）机器人长连接
     * 参数:botId-机器人ID, secret-长连接密钥
     * 返回数据:无（连接失败抛业务异常）
     * */
    public synchronized void connect(String botId, String secret) {
        AibotConnection existing = connections.get(botId);
        if (existing != null && existing.isOpen()) {
            return;
        }
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wecom-aibot-keeper");
                t.setDaemon(true);
                return t;
            });
        }
        try {
            AibotConnection conn = new AibotConnection(botId, secret);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(conn, URI.create(WSS_URL));
            // 同步等待订阅结果（订阅有频率保护，失败不能盲目重试）
            JsonNode subResult = sendAndWait(conn, subscribeFrame(botId, secret), 8);
            if (subResult == null || subResult.path("errcode").asInt(-1) != 0) {
                conn.closeSilently();
                connections.remove(botId);
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                        "企微智能机器人订阅失败: " + (subResult != null ? subResult.path("errmsg").asText("无响应") : "无响应"));
            }
            connections.put(botId, conn);
            conn.startHeartbeat();
            log.info("企微智能机器人长连接已建立 botId={}", botId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                    "企微智能机器人连接失败: " + ex.getMessage());
        }
    }

    /*
     * 方法功能:断开机器人长连接（渠道停用/删除时）
     * 参数:botId-机器人ID
     * 返回数据:无
     * */
    public synchronized void disconnect(String botId) {
        AibotConnection conn = connections.remove(botId);
        if (conn != null) {
            conn.closeSilently();
            log.info("企微智能机器人长连接已断开 botId={}", botId);
        }
    }

    /*
     * 方法功能:连接是否就绪
     * 参数:botId-机器人ID
     * 返回数据:boolean
     * */
    public boolean isConnected(String botId) {
        AibotConnection conn = connections.get(botId);
        return conn != null && conn.isOpen();
    }

    /*
     * 方法功能:发送 markdown 消息到会话（aibot_send_msg，官方告警推送口径）
     * 参数:botId-机器人ID, secret-长连接密钥（未连接时自动建连）, chatid-目标会话, content-markdown 文本
     * 返回数据:无（失败抛业务异常）
     * */
    public void sendMarkdown(String botId, String secret, String chatid, String content) {
        AibotConnection conn = connections.get(botId);
        if (conn == null || !conn.isOpen()) {
            connect(botId, secret);
            conn = connections.get(botId);
        }
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("cmd", "aibot_send_msg");
        ObjectNode headers = frame.putObject("headers");
        headers.put("req_id", UUID.randomUUID().toString());
        ObjectNode body = frame.putObject("body");
        body.put("chatid", chatid);
        // chat_type 由注册表已知类型决定；未知按官方建议优先群聊（0/缺省行为）
        if (chatRegistry.known(botId, chatid)) {
            var entry = chatRegistry.listByBot(botId).stream()
                    .filter(e -> e.chatid().equals(chatid)).findFirst().orElse(null);
            if (entry != null) {
                body.put("chat_type", "group".equals(entry.chatType()) ? 2 : 1);
            }
        }
        body.put("msgtype", "markdown");
        body.putObject("markdown").put("content", content);

        JsonNode result = sendAndWait(conn, frame.toString(), SEND_TIMEOUT_SECONDS);
        if (result == null || result.path("errcode").asInt(-1) != 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                    "企微智能机器人推送失败: " + (result != null
                            ? result.path("errcode").asInt() + " " + result.path("errmsg").asText()
                            : "响应超时"));
        }
    }

    // ===================== 帧构造/收发 =====================

    private String subscribeFrame(String botId, String secret) {
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("cmd", "aibot_subscribe");
        ObjectNode headers = frame.putObject("headers");
        headers.put("req_id", UUID.randomUUID().toString());
        ObjectNode body = frame.putObject("body");
        body.put("bot_id", botId);
        body.put("secret", secret);
        return frame.toString();
    }

    private String pingFrame() {
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("cmd", "ping");
        frame.putObject("headers").put("req_id", UUID.randomUUID().toString());
        return frame.toString();
    }

    // 同步发送并等待 req_id 匹配的响应
    private JsonNode sendAndWait(AibotConnection conn, String json, int timeoutSeconds) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String reqId = node.path("headers").path("req_id").asText();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            if (reqId != null && !reqId.isEmpty()) {
                pending.put(reqId, future);
            }
            conn.send(json);
            if (reqId == null || reqId.isEmpty()) {
                return null;
            }
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            return null;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,
                    "企微智能机器人通信异常: " + ex.getMessage());
        }
    }

    // ===================== 连接端点（每机器人一个实例） =====================

    @ClientEndpoint
    public class AibotConnection {

        private final String botId;
        private final String secret;
        private Session session;
        private ScheduledFuture<?> heartbeatTask;
        private ScheduledFuture<?> reconnectTask;
        private volatile boolean closedByUser;

        AibotConnection(String botId, String secret) {
            this.botId = botId;
            this.secret = secret;
        }

        @OnOpen
        public void onOpen(Session session) {
            this.session = session;
        }

        @OnMessage
        public void onMessage(String message) {
            try {
                JsonNode node = objectMapper.readTree(message);
                String cmd = node.path("cmd").asText("");
                String reqId = node.path("headers").path("req_id").asText(null);
                // 响应帧：唤醒等待者
                if (reqId != null && pending.containsKey(reqId)) {
                    pending.remove(reqId).complete(node);
                }
                // 回调帧：登记会话（chatid 只能从这里来）
                if ("aibot_msg_callback".equals(cmd) || "aibot_event_callback".equals(cmd)) {
                    JsonNode body = node.path("body");
                    String chatid = body.path("chatid").asText(null);
                    if (chatid != null && !chatid.isEmpty()) {
                        chatRegistry.record(botId, chatid,
                                body.path("chattype").asText("group"),
                                body.path("from").path("userid").asText(null),
                                body.path("text").path("content").asText(null));
                    }
                }
                // 被新连接踢下线：官方 disconnected_event
                if ("aibot_event_callback".equals(cmd)
                        && "disconnected_event".equals(node.path("body").path("event").path("eventtype").asText())) {
                    log.warn("企微智能机器人连接被新连接取代 botId={}", botId);
                    closedByUser = true; // 不自动重连（另一实例接管）
                }
            } catch (Exception ex) {
                log.warn("企微智能机器人回调处理异常 botId={} error={}", botId, ex.getMessage());
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            stopHeartbeat();
            connections.remove(botId, this);
            if (!closedByUser) {
                log.warn("企微智能机器人连接断开，{}s 后重连 botId={} reason={}",
                        RECONNECT_DELAY_SECONDS, botId, reason);
                scheduleReconnect();
            }
        }

        @OnError
        public void onError(Session session, Throwable error) {
            log.warn("企微智能机器人连接错误 botId={} error={}", botId, error.getMessage());
        }

        void startHeartbeat() {
            heartbeatTask = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    session.getBasicRemote().sendText(pingFrame());
                } catch (Exception ex) {
                    log.warn("企微智能机器人心跳失败 botId={} error={}", botId, ex.getMessage());
                }
            }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        }

        void stopHeartbeat() {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
        }

        private void scheduleReconnect() {
            if (scheduler == null) {
                return;
            }
            reconnectTask = scheduler.schedule(() -> {
                try {
                    connect(botId, secret);
                } catch (Exception ex) {
                    log.warn("企微智能机器人重连失败，将随下次发送再试 botId={} error={}", botId, ex.getMessage());
                }
            }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }

        boolean isOpen() {
            return session != null && session.isOpen();
        }

        void send(String json) throws IOException {
            session.getBasicRemote().sendText(json);
        }

        void closeSilently() {
            closedByUser = true;
            stopHeartbeat();
            connections.remove(botId, this);
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
