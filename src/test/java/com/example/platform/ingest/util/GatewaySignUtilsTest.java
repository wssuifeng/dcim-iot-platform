package com.example.platform.ingest.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关签名与幂等键工具单元测试
 */
class GatewaySignUtilsTest {

    @Test
    void signatureIsDeterministic() {
        String sig1 = GatewaySignUtils.signature("secret-1", "{\"items\":[]}");
        String sig2 = GatewaySignUtils.signature("secret-1", "{\"items\":[]}");
        assertThat(sig1).isEqualTo(sig2).hasSize(64);
    }

    @Test
    void signatureDiffersBySecret() {
        String body = "{\"items\":[]}";
        assertThat(GatewaySignUtils.signature("secret-1", body))
                .isNotEqualTo(GatewaySignUtils.signature("secret-2", body));
    }

    @Test
    void signatureDiffersByBody() {
        String secret = "secret-1";
        assertThat(GatewaySignUtils.signature(secret, "{\"a\":1}"))
                .isNotEqualTo(GatewaySignUtils.signature(secret, "{\"a\":2}"));
    }

    @Test
    void verifyAcceptsCorrectSignature() {
        byte[] body = "{\"items\":[]}".getBytes();
        String signature = GatewaySignUtils.signature("secret-1", body);
        assertThat(GatewaySignUtils.verify("secret-1", body, signature)).isTrue();
    }

    @Test
    void verifyRejectsWrongSignatureAndMalformedInput() {
        byte[] body = "{\"items\":[]}".getBytes();
        String signature = GatewaySignUtils.signature("secret-1", body);
        assertThat(GatewaySignUtils.verify("secret-2", body, signature)).isFalse();
        assertThat(GatewaySignUtils.verify("secret-1", body, "deadbeef")).isFalse();
        assertThat(GatewaySignUtils.verify("secret-1", body, null)).isFalse();
        assertThat(GatewaySignUtils.verify("secret-1", body, "")).isFalse();
    }

    @Test
    void verifyIsCaseInsensitiveOnProvidedSignature() {
        byte[] body = "{\"items\":[]}".getBytes();
        String signature = GatewaySignUtils.signature("secret-1", body);
        assertThat(GatewaySignUtils.verify("secret-1", body, signature.toUpperCase())).isTrue();
    }

    @Test
    void dedupKeyCombinesAllComponents() {
        String key = GatewaySignUtils.dedupKey("DEV-1", "temperature", 100);
        assertThat(key).hasSize(64);
        assertThat(key)
                .isNotEqualTo(GatewaySignUtils.dedupKey("DEV-2", "temperature", 100))
                .isNotEqualTo(GatewaySignUtils.dedupKey("DEV-1", "humidity", 100))
                .isNotEqualTo(GatewaySignUtils.dedupKey("DEV-1", "temperature", 101));
    }
}
