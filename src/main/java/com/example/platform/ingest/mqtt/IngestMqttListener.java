package com.example.platform.ingest.mqtt;

import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import com.example.platform.service.SysAuditLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:数据接收门 MQTT 适配器，订阅网关北向 topic 并转投传输无关核心服务
 *
 * 【topic 契约】dcim/{accessKey}/samples（QoS1，报文 {"signature","batch"}）、
 *   dcim/{accessKey}/heartbeat（QoS0）、dcim/{accessKey}/lwt（遗嘱，broker 代发）；
 *   处理结果回执发布到 dcim/{accessKey}/ack。
 * 【验签语义】signature = sha256(secret + batchJson)，batchJson 为报文内嵌 batch
 *   对象的紧凑 JSON 序列化（与网关侧签名时的序列化保持一致）。
 * 【失败语义】凭证无效/验签失败的消息直接丢弃并记结构化审计（QoS1 由 broker
 *   重投，重投同样被拒不影响平台）；MQTT 不经 Spring Security 链，自行验签。
 * 【开放命名空间（S8-5）】dcim-open/{mqttUsername}/samples——MQTT 子凭证直连
 *   通道，无 HMAC（既定取舍，ADR-008/评审定稿）；机房归属来自凭证映射，
 *   username↔topic 前缀一致性双重校验；无 seq 幂等降级 sampleTime。
 *   【安全审查点（后期专项）】本命名空间依赖 broker 连接层鉴权 + publish-only
 *   ACL（dcim-open/${username}/#）；平台侧仅做归属与格式校验，无速率限制
 *   （单租户口径，后续专项补 per-username 限流）。
 * 【连接韧性】broker 不可用时后台定时重连，不阻塞应用启动（HTTP 通道独立可用）；
 *   cleanSession=true 下 Paho 自动重连成功后不会自动重订阅（broker 侧订阅状态随
 *   会话丢弃），由 MqttCallbackExtended.connectComplete 回调补订全部 topic（W-001
 *   修复），并有 MQTT_INGEST_STALL 假死巡检兜底（task/MqttIngestLivenessTask）。
 * */
@Slf4j
@Component
@ConditionalOnProperty(name = "ingest.mqtt.enabled", havingValue = "true")
public class IngestMqttListener {

    private static final String TOPIC_SAMPLES = "dcim/+/samples";
    private static final String TOPIC_HEARTBEAT = "dcim/+/heartbeat";
    private static final String TOPIC_LWT = "dcim/+/lwt";
    // S8-5 开放命名空间：MQTT 子凭证直连（无 HMAC），报文即 batch JSON
    private static final String TOPIC_OPEN_SAMPLES = "dcim-open/+/samples";
    private static final String OPEN_PREFIX = "dcim-open/";
    private static final String ACK_TOPIC_SUFFIX = "/ack";

    private final IngestCoreService ingestCoreService;
    private final com.example.platform.ingest.metrics.IngestMetricsCollector metricsCollector;
    private final IngestCredentialService ingestCredentialService;
    private final SysAuditLogService sysAuditLogService;
    private final com.example.platform.service.AlarmEventService alarmEventService;
    private final com.example.platform.service.CloudRoomService cloudRoomService;
    private final com.example.platform.ingest.service.IngestPayloadMappingService payloadMappingService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    private final String brokerUrl;
    private final String clientId;
    private final String username;
    private final String password;
    private final int keepAliveSeconds;
    private final boolean ackEnabled;

    private MqttClient client;
    private ScheduledExecutorService reconnectExecutor;
    // 假死巡检水位：最近一次收到任何订阅 topic 消息的时间戳（自监控用，W-001 配套）
    private volatile long lastIngestMessageMillis = System.currentTimeMillis();

    public IngestMqttListener(IngestCoreService ingestCoreService,
                              com.example.platform.ingest.metrics.IngestMetricsCollector metricsCollector,
                              IngestCredentialService ingestCredentialService,
                              SysAuditLogService sysAuditLogService,
                              com.example.platform.service.AlarmEventService alarmEventService,
                              com.example.platform.service.CloudRoomService cloudRoomService,
                              com.example.platform.ingest.service.IngestPayloadMappingService payloadMappingService,
                              Validator validator,
                              ObjectMapper objectMapper,
                              @Value("${ingest.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
                              @Value("${ingest.mqtt.client-id:platform-ingest}") String clientId,
                              @Value("${ingest.mqtt.username:}") String username,
                              @Value("${ingest.mqtt.password:}") String password,
                              @Value("${ingest.mqtt.keep-alive-seconds:30}") int keepAliveSeconds,
                              @Value("${ingest.mqtt.ack-enabled:true}") boolean ackEnabled) {
        this.ingestCoreService = ingestCoreService;
        this.metricsCollector = metricsCollector;
        this.ingestCredentialService = ingestCredentialService;
        this.sysAuditLogService = sysAuditLogService;
        this.alarmEventService = alarmEventService;
        this.cloudRoomService = cloudRoomService;
        this.payloadMappingService = payloadMappingService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.keepAliveSeconds = keepAliveSeconds;
        this.ackEnabled = ackEnabled;
    }

    /*
     * 方法功能:建立 broker 连接并订阅三个北向 topic，失败转后台重连
     * 参数:无
     * 返回数据:无
     * */
    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            // 回调须在 connect 前注册：首次连接与自动重连成功都会触发 connectComplete 补订
            client.setCallback(new ResubscribeCallback());
            connectAndSubscribe();
            log.info("MQTT 数据接收门已启动 broker={} clientId={}", brokerUrl, clientId);
        } catch (MqttException ex) {
            log.warn("MQTT broker 连接失败，进入后台重连（HTTP 通道不受影响）: {}", ex.getMessage());
            scheduleReconnect();
        }
    }

    /*
     * 方法功能:断开并释放 MQTT 客户端与重连调度器
     * 参数:无
     * 返回数据:无
     * */
    @PreDestroy
    public void stop() {
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (MqttException ex) {
                log.warn("MQTT 客户端关闭异常: {}", ex.getMessage());
            }
        }
    }

    /*
     * 方法功能:处理 samples topic 消息——解析 envelope、验签、校验后转投核心服务并回执
     * 参数:topic-消息 topic（dcim/{accessKey}/samples）, payload-原始报文字节
     * 返回数据:无
     * */
    void handleSamples(String topic, byte[] payload) {
        String accessKey = accessKeyOf(topic);
        noteIngestTraffic();
        try {
            IngestCredential credential = ingestCredentialService.authenticate(accessKey);

            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode signatureNode = envelope.get("signature");
            JsonNode batchNode = envelope.get("batch");
            if (signatureNode == null || batchNode == null || batchNode.isMissingNode()) {
                auditRejected(accessKey, "MQTT 报文缺少 signature 或 batch 字段");
                return;
            }
            // 验签对象为报文内嵌 batch 对象的紧凑序列化（与网关侧序列化约定一致）
            byte[] batchJson = objectMapper.writeValueAsBytes(batchNode);
            if (!GatewaySignUtils.verify(credential.getSecret(), batchJson, signatureNode.asText())) {
                auditRejected(accessKey, "MQTT 报文验签失败");
                return;
            }

            IngestSampleBatchDTO batch = objectMapper.treeToValue(batchNode, IngestSampleBatchDTO.class);
            var violations = validator.validate(batch);
            if (!violations.isEmpty()) {
                auditRejected(accessKey, "MQTT 报文校验失败: " + violations.iterator().next().getMessage());
                return;
            }

            long start = System.nanoTime();
            IngestResultVO result = ingestCoreService.processSamples(credential, batch);
            metricsCollector.recordBatch("mqtt", batch.getItems().size(),
                    (System.nanoTime() - start) / 1_000_000,
                    result.getAccepted(), result.getDuplicated(), result.getFailed());
            publishAck(accessKey, Result.success(result));
        } catch (BusinessException ex) {
            auditRejected(accessKey, "MQTT 凭证鉴权失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("MQTT 消息处理异常 topic={}", topic, ex);
        }
    }

    /*
     * 方法功能:处理开放命名空间 samples 消息（S8-5）——username→凭证→机房归属，
     *   username↔topic 前缀双重校验；报文先按标准 batch JSON 解析（无签名/无 envelope），
     *   解析失败或校验不过时回退自定义报文映射（ADR-009：特征匹配转译，未命中留存原始报文）
     * 参数:topic-消息 topic（dcim-open/{mqttUsername}/samples）, payload-batch JSON 字节
     * 返回数据:无
     * */
    void handleOpenSamples(String topic, byte[] payload) {
        String mqttUsername = topic.startsWith(OPEN_PREFIX) && topic.length() > OPEN_PREFIX.length()
                ? topic.substring(OPEN_PREFIX.length()).split("/")[0] : "unknown";
        noteIngestTraffic();
        try {
            IngestCredential credential = ingestCredentialService.authenticateByMqttUsername(mqttUsername);
            // 双重校验：topic 第二层必须与凭证用户名一致（防跨用户 topic 混投）
            if (!mqttUsername.equals(credential.getMqttUsername())) {
                auditRejected(mqttUsername, "开放通道 username 与 topic 前缀不一致");
                return;
            }
            JsonNode root = null;
            IngestSampleBatchDTO batch = null;
            try {
                root = objectMapper.readTree(payload);
                batch = objectMapper.treeToValue(root, IngestSampleBatchDTO.class);
                var violations = validator.validate(batch);
                if (!violations.isEmpty()) {
                    batch = null;
                }
            } catch (Exception ex) {
                batch = null;
            }

            long start = System.nanoTime();
            IngestResultVO result;
            int itemCount;
            if (batch != null) {
                result = ingestCoreService.processSamples(credential, batch);
                itemCount = batch.getItems().size();
            } else {
                // 标准报文解析失败：回退自定义报文映射（未命中时留存原始报文）
                String body = new String(payload, StandardCharsets.UTF_8);
                var translated = payloadMappingService.tryTranslate(credential, root, body);
                if (translated.isEmpty()) {
                    auditRejected(mqttUsername, "开放通道报文既非标准 batch 也未匹配映射模板，"
                            + "原始报文已留存（可在零散设备接入页配置映射）");
                    return;
                }
                result = ingestCoreService.processSamples(credential, translated.get());
                itemCount = translated.get().getItems().size();
            }
            metricsCollector.recordBatch("mqtt", itemCount,
                    (System.nanoTime() - start) / 1_000_000,
                    result.getAccepted(), result.getDuplicated(), result.getFailed());
        } catch (BusinessException ex) {
            auditRejected(mqttUsername, "开放通道子凭证鉴权失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("MQTT 开放通道消息处理异常 topic={}", topic, ex);
        }
    }

    /*
     * 方法功能:处理 heartbeat topic 消息，刷新凭证存活时间
     * 参数:topic-消息 topic, payload-原始报文字节（内容不校验）
     * 返回数据:无
     * */
    void handleHeartbeat(String topic, byte[] payload) {
        String accessKey = accessKeyOf(topic);
        noteIngestTraffic();
        try {
            IngestCredential credential = ingestCredentialService.authenticate(accessKey);
            ingestCoreService.heartbeat(credential);
        } catch (BusinessException ex) {
            auditRejected(accessKey, "MQTT 心跳凭证鉴权失败: " + ex.getMessage());
        }
    }

    /*
     * 方法功能:处理 LWT 遗嘱消息（broker 检测网关断连后代发），记录审计与离线观测日志
     * 参数:topic-消息 topic, payload-遗嘱内容
     * 返回数据:无
     * */
    void handleLwt(String topic, byte[] payload) {
        String accessKey = accessKeyOf(topic);
        noteIngestTraffic();
        String will = new String(payload, StandardCharsets.UTF_8);
        log.warn("MQTT 网关遗嘱触发（断连）accessKey={} will={}", accessKey, will);
        sysAuditLogService.logStructured(null, accessKey, "GATEWAY", null,
                "INGEST_GATEWAY_LWT", "MQTT_GATEWAY", accessKey, "FAILURE",
                String.valueOf(ResultCode.INGEST_UNAUTHORIZED), "mqtt", null,
                "网关 MQTT 断连遗嘱触发，last_seen_at 停滞将呈现离线");
        // LWT 立即产出 GATEWAY_OFFLINE 告警（不等 5 分钟轮询扫描）
        triggerGatewayOffline(accessKey);
    }

    private String roomNameOf(Long roomId) {
        return cloudRoomService.getById(roomId) != null
                ? cloudRoomService.getById(roomId).getRoomName() : String.valueOf(roomId);
    }

    // 遗嘱触发即告警：凭证存在且启用时产出设备级（device_id 为空）离线告警
    private void triggerGatewayOffline(String accessKey) {
        try {
            IngestCredential credential = ingestCredentialService.authenticate(accessKey);
            alarmEventService.trigger(com.example.platform.dto.AlarmTriggerContext.builder()
                    .deviceId(null)
                    .pointId(null)
                    .alarmType("GATEWAY_OFFLINE")
                    .alarmLevel(2)
                    .alarmMessage("网关离线：MQTT 遗嘱触发（机房["
                            + roomNameOf(credential.getCloudRoomId()) + "] 网关断连）")
                    .cloudRoomId(credential.getCloudRoomId())
                    .build());
        } catch (Exception ex) {
            // 凭证无效/停用的遗嘱无从归属，仅保留审计日志
            log.debug("LWT 告警未产出 accessKey={} error={}", accessKey, ex.getMessage());
        }
    }

    private void connectAndSubscribe() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(keepAliveSeconds);
        options.setConnectionTimeout(10);
        if (!username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        client.connect(options);
        subscribeAll();
    }

    /*
     * 方法功能:订阅全部北向 topic（samples/heartbeat/lwt/open）
     * 参数:target-目标客户端（连接回调与测试可注入）
     * 返回数据:无
     * 说明:Paho 同过滤器重复订阅为替换语义，幂等；首次连接（connectAndSubscribe）与
     *   自动重连（connectComplete）都会调用——cleanSession=true 下自动重连不重订阅，
     *   缺此补订会导致通道静默假死（W-001）
     * */
    void subscribeAll(IMqttClient target) throws MqttException {
        target.subscribe(TOPIC_SAMPLES, 1, (t, m) -> handleSamples(t, m.getPayload()));
        target.subscribe(TOPIC_HEARTBEAT, 0, (t, m) -> handleHeartbeat(t, m.getPayload()));
        target.subscribe(TOPIC_LWT, 0, (t, m) -> handleLwt(t, m.getPayload()));
        // S8-5 开放命名空间（MQTT 子凭证直连）：报文直入传输无关核心
        target.subscribe(TOPIC_OPEN_SAMPLES, 1, (t, m) -> handleOpenSamples(t, m.getPayload()));
    }

    private void subscribeAll() throws MqttException {
        subscribeAll(client);
    }

    /*
     * 方法功能:连接完成回调处理——全量补订（测试可注入客户端）
     * 参数:target-目标客户端, reconnect-是否自动重连, serverURI-broker 地址
     * 返回数据:无
     * */
    void handleConnectComplete(IMqttClient target, boolean reconnect, String serverURI) throws MqttException {
        subscribeAll(target);
        log.info("MQTT 订阅就绪 reconnect={} broker={}", reconnect, serverURI);
    }

    /*
     * 类功能:Paho 连接回调——connectComplete 在首次连接与自动重连成功后均触发，
     *   在此全量补订；connectionLost 仅留痕，重连由 automaticReconnect 负责
     * */
    private class ResubscribeCallback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            try {
                handleConnectComplete(client, reconnect, serverURI);
            } catch (MqttException ex) {
                log.error("MQTT 重订阅失败（通道将静默失效，假死巡检会告警）: {}", ex.getMessage(), ex);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("MQTT 连接断开，等待自动重连 broker={} reason={}", brokerUrl,
                    cause == null ? "unknown" : cause.getMessage());
        }

        // 消息分发走 subscribe 的 per-topic listener，全局回调不承接消息
        @Override
        public void messageArrived(String topic, MqttMessage message) { }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) { }
    }

    private void scheduleReconnect() {
        if (reconnectExecutor != null) {
            return;
        }
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mqtt-ingest-reconnect");
            thread.setDaemon(true);
            return thread;
        });
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (client == null) {
                    client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                    // 新建客户端同样要挂回调，否则后续自动重连无补订能力（W-001）
                    client.setCallback(new ResubscribeCallback());
                }
                connectAndSubscribe();
                log.info("MQTT broker 重连成功 broker={}", brokerUrl);
                reconnectExecutor.shutdown();
                reconnectExecutor = null;
            } catch (MqttException ex) {
                log.debug("MQTT broker 重连失败，10 秒后重试: {}", ex.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void publishAck(String accessKey, Result<IngestResultVO> result) {
        if (!ackEnabled || client == null || !client.isConnected()) {
            return;
        }
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(result));
            message.setQos(0);
            client.publish("dcim/" + accessKey + ACK_TOPIC_SUFFIX, message);
        } catch (Exception ex) {
            log.warn("MQTT 回执发布失败 accessKey={}: {}", accessKey, ex.getMessage());
        }
    }

    private String accessKeyOf(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[1] : "unknown";
    }

    private void auditRejected(String accessKey, String detail) {
        log.warn("MQTT 上报被拒 accessKey={} reason={}", accessKey, detail);
        sysAuditLogService.logStructured(null, accessKey, "GATEWAY", null,
                "INGEST_MQTT_REJECTED", "MQTT_MESSAGE", accessKey, "REJECTED",
                String.valueOf(ResultCode.INGEST_UNAUTHORIZED), "mqtt", null, detail);
    }

    // 供测试断言连接状态
    boolean isConnected() {
        return client != null && client.isConnected();
    }

    /*
     * 方法功能:MQTT 连接状态查询（假死巡检任务用）
     * 参数:无
     * 返回数据:true=客户端已连接
     * */
    public boolean isMqttConnected() {
        return client != null && client.isConnected();
    }

    /*
     * 方法功能:最近一次收到任何订阅 topic 消息距今的毫秒数（假死巡检任务用）
     * 参数:无
     * 返回数据:毫秒数；启动后尚无消息时为进程内经历时长
     * */
    public long millisSinceLastIngestMessage() {
        return System.currentTimeMillis() - lastIngestMessageMillis;
    }

    // 任何订阅 topic 消息到达即刷新假死巡检水位
    private void noteIngestTraffic() {
        lastIngestMessageMillis = System.currentTimeMillis();
    }
}
