package com.dcim.simulator.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 平台接收门客户端：网关对云平台的唯一交互面（Gateway 签名，零管理账号依赖）
 *
 * 能力：心跳自检、阈值与机房目录拉取（ON_ALARM 本地判定的配置源）。
 * 签名：Authorization: Gateway {accessKey}:{sha256(secret + body)}。
 */
@Slf4j
public class PlatformIngestClient {

    private final String baseUrl;
    private final String accessKey;
    private final String secret;
    private final RestClient restClient = RestClient.create();

    public PlatformIngestClient(String baseUrl, String accessKey, String secret) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.accessKey = accessKey;
        this.secret = secret;
    }

    /**
     * 连通自检：Gateway 签名心跳，成功即网关配置正确且平台可达
     */
    public boolean ping() {
        try {
            String body = "{\"ts\":\"" + java.time.LocalDateTime.now() + "\"}";
            restClient.post()
                    .uri(baseUrl + "/api/ingest/heartbeat")
                    .header("Authorization", gatewayHeader(body))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve().body(String.class);
            return true;
        } catch (Exception ex) {
            log.warn("平台连通自检失败: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 拉取阈值与机房目录：{thresholds:[{pointCode,operator,threshold,alarmLevel}], rooms:[{roomId,accessKey}]}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> exportConfig() {
        // GET 空 body：签名为 sha256(secret)
        return restClient.get()
                .uri(baseUrl + "/api/ingest/export/thresholds")
                .header("Authorization", gatewayHeader(""))
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> thresholdsOf(Map<String, Object> export) {
        Object data = export == null ? null : ((Map<String, Object>) export.get("data"));
        if (data instanceof Map<?, ?> m && m.get("thresholds") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> roomsOf(Map<String, Object> export) {
        Object data = export == null ? null : ((Map<String, Object>) export.get("data"));
        if (data instanceof Map<?, ?> m && m.get("rooms") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private String gatewayHeader(String body) {
        return "Gateway " + accessKey + ":" + sign(body);
    }

    /**
     * 签名：hex(sha256(secret + body))
     */
    private String sign(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
