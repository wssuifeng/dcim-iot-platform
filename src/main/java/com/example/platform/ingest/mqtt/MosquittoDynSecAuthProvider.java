package com.example.platform.ingest.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/*
 * 类功能:mosquitto dynamic-security 鉴权后端实现（S8-5）
 *
 * 【协议】向 $CONTROL/dynamic-security/v1 发布 JSON 控制命令：
 *   createClient（含密码与 dcim-open 角色）。前置要求（部署侧一次性配置）：
 *   broker 启用 dynamic-security 且预建角色 dcim-open，其 ACL 为
 *   publishClientSend "dcim-open/${username}/#"（${username} 由 broker 替换，
 *   天然实现用户间隔离；角色不含订阅权限 = 最小权限）。
 * 【安全审查点（后期专项覆盖）】平台管理账号（dynsec admin）凭据存储于
 *   ingest.mqtt.broker-auth-* 配置，需与数据面凭证隔离并限制网络可达性。
 * 【失败语义】供给失败抛异常给调用方（签发流程记日志人工兜底）——broker 不认识
 *   该用户时开放通道连接被 broker 拒绝，方向为 fail closed。
 * */
@Slf4j
@Component
@ConditionalOnProperty(name = "ingest.mqtt.broker-auth", havingValue = "mosquitto-dynsec")
public class MosquittoDynSecAuthProvider implements MqttAuthProvider {

    private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";

    private final String brokerUrl;
    private final String adminUsername;
    private final String adminPassword;

    public MosquittoDynSecAuthProvider(
            @Value("${ingest.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
            @Value("${ingest.mqtt.broker-auth-admin-username:}") String adminUsername,
            @Value("${ingest.mqtt.broker-auth-admin-password:}") String adminPassword) {
        this.brokerUrl = brokerUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void upsertUser(String username, String password, String topicPattern) {
        // dynsec createClient 幂等性由 setClientPassword 兜底：先 create（已存在则忽略失败）再 set 密码
        sendControl(String.format(
                "{\"command\":\"createClient\",\"username\":\"%s\",\"password\":\"%s\","
                        + "\"textname\":\"dcim-open-channel\",\"roles\":[{\"rolename\":\"dcim-open\"}]}",
                escape(username), escape(password)));
        sendControl(String.format(
                "{\"command\":\"setClientPassword\",\"username\":\"%s\",\"password\":\"%s\"}",
                escape(username), escape(password)));
        log.info("mosquitto dynsec 用户供给完成 username={} topic={}", username, topicPattern);
    }

    @Override
    public void removeUser(String username) {
        sendControl(String.format("{\"command\":\"deleteClient\",\"username\":\"%s\"}", escape(username)));
        log.info("mosquitto dynsec 用户删除完成 username={}", username);
    }

    // 短连接发送控制命令（供给频率=人工签发，无需常驻客户端）；响应不等待——
    // 供给结果由"开放通道能否连接"最终验证，失败方向安全
    private void sendControl(String commandJson) {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            if (!adminUsername.isEmpty()) {
                options.setUserName(adminUsername);
                options.setPassword(adminPassword.toCharArray());
            }
            IMqttClient client = new MqttClient(brokerUrl,
                    "platform-dynsec-" + Long.toHexString(System.nanoTime()),
                    new MemoryPersistence());
            try {
                client.connect(options);
                MqttMessage message = new MqttMessage(commandJson.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                client.publish(CONTROL_TOPIC, message);
            } finally {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("mosquitto dynsec 控制命令发送失败: " + ex.getMessage(), ex);
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
