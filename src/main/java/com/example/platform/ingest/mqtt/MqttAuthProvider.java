package com.example.platform.ingest.mqtt;

/*
 * 类功能:MQTT broker 连接层鉴权后端接口（S8-5 抽象）
 *
 * 【为什么必须有这层】连接层鉴权只能落在 broker 侧（平台 MQTT listener 拿不到
 *   CONNECT 报文里的凭证）；平台侧职责是"签发子凭证 → 通知 broker 建用户"。
 * 【实现位】默认 LoggingMqttAuthProvider（仅记录，需人工配置 broker）；
 *   MosquittoDynSecAuthProvider 走 mosquitto dynamic-security 控制主题。
 *   换 EMQX 等时新增实现即可，平台核心不感知 broker 细节。
 * 【安全审查点（后期专项覆盖）】broker 侧 ACL 必须约束为
 *   publish-only dcim-open/{username}/#，禁止订阅 $CONTROL 与通配跨用户 topic。
 * */
public interface MqttAuthProvider {

    /*
     * 方法功能:在 broker 侧创建/更新连接层用户并授予开放命名空间 publish-only 权限
     * 参数:username-连接用户名, password-连接密码, topicPattern-允许发布的 topic（含通配）
     * 返回数据:无
     * 抛出异常:供给失败抛 RuntimeException（调用方记日志人工兜底，不阻断凭证签发）
     * */
    void upsertUser(String username, String password, String topicPattern);

    /*
     * 方法功能:删除 broker 侧连接层用户（子凭证作废时调用）
     * 参数:username-连接用户名
     * 返回数据:无
     * */
    void removeUser(String username);
}
