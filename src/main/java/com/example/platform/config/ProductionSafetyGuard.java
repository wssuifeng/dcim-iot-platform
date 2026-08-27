package com.example.platform.config;

import com.example.platform.common.exception.ProductionSafetyException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/*
 * 类功能:生产环境启动期安全门控，阻断默认密钥、通配 Origin 和危险功能开关
 * */
@Component
public class ProductionSafetyGuard {

    static final String DEFAULT_JWT_SECRET = "iot-platform-jwt-secret-key-must-be-at-least-32-chars";
    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final int MAX_INDEXED_PROPERTY_ITEMS = 32;

    private final Environment environment;

    public ProductionSafetyGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!isProdProfileActive()) {
            return;
        }

        List<String> violations = validateProductionSettings();
        if (!violations.isEmpty()) {
            throw new ProductionSafetyException("生产环境安全配置不合规: " + String.join("; ", violations));
        }
    }

    List<String> validateProductionSettings() {
        List<String> violations = new ArrayList<>();

        requireJwtSecret(violations);
        requireSpringDocDisabled(violations);
        requireTrustedOrigins("cors.allowed-origins", "CORS", violations);
        requireTrustedOrigins("websocket.allowed-origins", "WebSocket", violations);
        requireTextProperty("spring.data.redis.password", "生产环境 Redis 密码不能为空", violations);
        requireTextProperty("spring.datasource.password", "生产环境数据库密码不能为空", violations);

        return violations;
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void requireJwtSecret(List<String> violations) {
        String secret = environment.getProperty("jwt.secret");
        if (!StringUtils.hasText(secret) || isUnresolvedPlaceholder(secret)) {
            violations.add("jwt.secret 必须通过环境变量设置");
            return;
        }
        if (DEFAULT_JWT_SECRET.equals(secret)) {
            violations.add("jwt.secret 不得使用默认开发密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            violations.add("jwt.secret 长度必须至少 32 字节");
        }
    }

    private void requireSpringDocDisabled(List<String> violations) {
        boolean apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true);
        boolean swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true);
        if (apiDocsEnabled) {
            violations.add("生产环境必须关闭 springdoc.api-docs.enabled");
        }
        if (swaggerUiEnabled) {
            violations.add("生产环境必须关闭 springdoc.swagger-ui.enabled");
        }
    }

    private void requireTrustedOrigins(String propertyName, String label, List<String> violations) {
        List<String> origins = getListProperty(propertyName);
        if (origins.isEmpty()) {
            violations.add(label + " Origin 必须显式配置可信域名");
            return;
        }

        for (String origin : origins) {
            String normalized = origin.toLowerCase(Locale.ROOT);
            if (origin.contains("*")) {
                violations.add(label + " Origin 不得使用通配: " + origin);
            }
            if (isDevelopmentOrigin(normalized)) {
                violations.add(label + " Origin 不得使用开发默认域名: " + origin);
            }
        }
    }

    private boolean isDevelopmentOrigin(String normalizedOrigin) {
        return normalizedOrigin.contains("localhost")
                || normalizedOrigin.contains("127.0.0.1")
                || normalizedOrigin.contains("0.0.0.0")
                || normalizedOrigin.contains("[::1]");
    }

    private void requireFalse(String propertyName, String message, List<String> violations) {
        boolean enabled = environment.getProperty(propertyName, Boolean.class, false);
        if (enabled) {
            violations.add(message);
        }
    }

    private void requireTextProperty(String propertyName, String message, List<String> violations) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
            violations.add(message);
        }
    }

    private List<String> getListProperty(String propertyName) {
        String rawValue = environment.getProperty(propertyName);
        if (StringUtils.hasText(rawValue)) {
            return splitList(rawValue);
        }

        List<String> values = new ArrayList<>();
        for (int i = 0; i < MAX_INDEXED_PROPERTY_ITEMS; i++) {
            String indexedValue = environment.getProperty(propertyName + "[" + i + "]");
            if (StringUtils.hasText(indexedValue)) {
                values.add(indexedValue.trim());
            }
        }
        return values;
    }

    private List<String> splitList(String value) {
        return Arrays.stream(StringUtils.commaDelimitedListToStringArray(value))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean isUnresolvedPlaceholder(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
    }
}
