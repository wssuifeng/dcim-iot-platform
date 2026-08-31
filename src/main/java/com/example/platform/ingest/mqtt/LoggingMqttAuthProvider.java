package com.example.platform.ingest.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/*
 * 类功能:MQTT 鉴权后端默认实现——仅记录供给动作，不与 broker 交互
 *
 * 【适用场景】本地开发 / 未启用 broker 侧自动供给的部署：子凭证照常签发落库，
 *   但 broker 侧（mosquitto/EMQX）需按记录的用户名/密码人工配置连接鉴权与
 *   publish-only ACL（dcim-open/{username}/#），否则开放通道连接被 broker 拒绝
 *   （fail closed，方向安全）。
 * 【启用】ingest.mqtt.broker-auth 未配置或不为 mosquitto-dynsec 时生效。
 * */
@Slf4j
@Component
@ConditionalOnProperty(name = "ingest.mqtt.broker-auth", havingValue = "none", matchIfMissing = true)
public class LoggingMqttAuthProvider implements MqttAuthProvider {

    @Override
    public void upsertUser(String username, String password, String topicPattern) {
        log.warn("MQTT 子凭证已签发但 broker 侧未自动供给（ingest.mqtt.broker-auth 未配置）——"
                + "请人工在 broker 配置用户 {} 与 publish-only ACL {}", username, topicPattern);
    }

    @Override
    public void removeUser(String username) {
        log.info("MQTT 子凭证作废（broker 侧用户需人工清理） username={}", username);
    }
}
