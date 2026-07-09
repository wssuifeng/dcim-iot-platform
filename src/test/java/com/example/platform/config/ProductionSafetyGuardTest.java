package com.example.platform.config;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.ProductionSafetyException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProductionSafetyGuardTest {

    @Test
    void validateSkipsUnsafeDefaultsOutsideProdProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", ProductionSafetyGuard.DEFAULT_JWT_SECRET)
                .withProperty("springdoc.api-docs.enabled", "true")
                .withProperty("springdoc.swagger-ui.enabled", "true")
                .withProperty("cors.allowed-origins", "*")
                .withProperty("websocket.allowed-origins", "*")
                .withProperty("simulation.write-enabled", "true");
        environment.setActiveProfiles("dev");

        ProductionSafetyGuard guard = new ProductionSafetyGuard(environment);

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsUnsafeProductionDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", ProductionSafetyGuard.DEFAULT_JWT_SECRET)
                .withProperty("springdoc.api-docs.enabled", "true")
                .withProperty("springdoc.swagger-ui.enabled", "true")
                .withProperty("cors.allowed-origins", "*")
                .withProperty("websocket.allowed-origins", "http://localhost:*")
                .withProperty("simulation.write-enabled", "true")
                .withProperty("spring.data.redis.password", "")
                .withProperty("spring.datasource.password", "");
        environment.setActiveProfiles("prod");

        ProductionSafetyGuard guard = new ProductionSafetyGuard(environment);

        Throwable throwable = catchThrowable(guard::validate);

        assertThat(throwable)
                .isInstanceOf(ProductionSafetyException.class)
                .hasMessageContaining("jwt.secret 不得使用默认开发密钥")
                .hasMessageContaining("springdoc.api-docs.enabled")
                .hasMessageContaining("CORS Origin 不得使用通配")
                .hasMessageContaining("WebSocket Origin 不得使用开发默认域名")
                .hasMessageContaining("生产环境必须关闭模拟写入开关")
                .hasMessageContaining("生产环境 Redis 密码不能为空")
                .hasMessageContaining("生产环境数据库密码不能为空");
        assertThat(((ProductionSafetyException) throwable).getCode())
                .isEqualTo(ResultCode.SUPPLY_CONFIG_UNSAFE);
    }

    @Test
    void validateAcceptsHardenedProductionSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", "0123456789abcdef0123456789abcdef")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("cors.allowed-origins", "https://ops.example.com,https://admin.example.com")
                .withProperty("websocket.allowed-origins", "https://ops.example.com")
                .withProperty("simulation.write-enabled", "false")
                .withProperty("spring.data.redis.password", "redis-prod-secret")
                .withProperty("spring.datasource.password", "db-prod-secret");
        environment.setActiveProfiles("prod");

        ProductionSafetyGuard guard = new ProductionSafetyGuard(environment);

        assertThat(guard.validateProductionSettings()).isEmpty();
        assertThatCode(guard::validate).doesNotThrowAnyException();
    }
}
