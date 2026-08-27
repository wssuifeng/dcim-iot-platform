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
 * 【连接韧性】broker 不可用时后台定时重连，不阻塞应用启动（HTTP 通道独立可用）。
 * */
@Slf4j
@Component
@ConditionalOnProperty(name = "ingest.mqtt.enabled", havingValue = "true")
public class IngestMqttListener {

    private static final String TOPIC_SAMPLES = "dcim/+/samples";
    private static final String TOPIC_HEARTBEAT = "dcim/+/heartbeat";
    private static final String TOPIC_LWT = "dcim/+/lwt";
    private static final String ACK_TOPIC_SUFFIX = "/ack";

    private final IngestCoreService ingestCoreService;
    private final IngestCredentialService ingestCredentialService;
    private final SysAuditLogService sysAuditLogService;
    private final com.example.platform.service.AlarmEventService alarmEventService;
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

    public IngestMqttListener(IngestCoreService ingestCoreService,
                              IngestCredentialService ingestCredentialService,
                              SysAuditLogService sysAuditLogService,
                              com.example.platform.service.AlarmEventService alarmEventService,
                              Validator validator,
                              ObjectMapper objectMapper,
                              @Value("${ingest.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
                              @Value("${ingest.mqtt.client-id:platform-ingest}") String clientId,
                              @Value("${ingest.mqtt.username:}") String username,
                              @Value("${ingest.mqtt.password:}") String password,
                              @Value("${ingest.mqtt.keep-alive-seconds:30}") int keepAliveSeconds,
                              @Value("${ingest.mqtt.ack-enabled:true}") boolean ackEnabled) {
        this.ingestCoreService = ingestCoreService;
        this.ingestCredentialService = ingestCredentialService;
        this.sysAuditLogService = sysAuditLogService;
        this.alarmEventService = alarmEventService;
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

            IngestResultVO result = ingestCoreService.processSamples(credential, batch);
            publishAck(accessKey, Result.success(result));
        } catch (BusinessException ex) {
            auditRejected(accessKey, "MQTT 凭证鉴权失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("MQTT 消息处理异常 topic={}", topic, ex);
        }
    }

    /*
     * 方法功能:处理 heartbeat topic 消息，刷新凭证存活时间
     * 参数:topic-消息 topic, payload-原始报文字节（内容不校验）
     * 返回数据:无
     * */
    void handleHeartbeat(String topic, byte[] payload) {
        String accessKey = accessKeyOf(topic);
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
        String will = new String(payload, StandardCharsets.UTF_8);
        log.warn("MQTT 网关遗嘱触发（断连）accessKey={} will={}", accessKey, will);
        sysAuditLogService.logStructured(null, accessKey, "GATEWAY", null,
                "INGEST_GATEWAY_LWT", "MQTT_GATEWAY", accessKey, "FAILURE",
                String.valueOf(ResultCode.INGEST_UNAUTHORIZED), "mqtt", null,
                "网关 MQTT 断连遗嘱触发，last_seen_at 停滞将呈现离线");
        // LWT 立即产出 GATEWAY_OFFLINE 告警（不等 5 分钟轮询扫描）
        triggerGatewayOffline(accessKey);
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
                    .alarmMessage("网关离线：MQTT 遗嘱触发（" + credential.getGatewayName() + " 断连）")
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
        client.subscribe(TOPIC_SAMPLES, 1, (t, m) -> handleSamples(t, m.getPayload()));
        client.subscribe(TOPIC_HEARTBEAT, 0, (t, m) -> handleHeartbeat(t, m.getPayload()));
        client.subscribe(TOPIC_LWT, 0, (t, m) -> handleLwt(t, m.getPayload()));
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
}
