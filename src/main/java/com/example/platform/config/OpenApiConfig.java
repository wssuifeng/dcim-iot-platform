package com.example.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 类功能:SpringDoc OpenAPI 文档配置，定义全局信息、JWT 安全方案与业务分组
 * */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    /** 全局 JWT 安全方案名称，应用于受保护接口 */
    public static final String SECURITY_SCHEME_NAME = "Bearer JWT";

    /**
     * 全局 OpenAPI 信息：标题、版本、联系方式 + JWT Bearer 认证方案
     * 启用后 Swagger UI 右上角出现 "Authorize" 按钮，填入 JWT 即可试调受保护接口
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT 平台 API 文档")
                        .description("基于 Spring Boot 4 的物联网设备管理与数据监控平台接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("IoT Platform Team")
                                .email("iot-platform@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .description("JWT 访问令牌，格式：Bearer &lt;token&gt;（登录接口 /auth/login 获取）")));
    }

    /** 设备管理分组：设备 / 设备数据 / 设备类型 */
    @Bean
    public GroupedOpenApi deviceGroup() {
        return GroupedOpenApi.builder()
                .group("01-设备管理")
                .pathsToMatch("/devices/**", "/device-data/**", "/device-types/**")
                .build();
    }

    /** 告警与监控分组：告警 / 仪表盘 / 指标字典 */
    @Bean
    public GroupedOpenApi alertMonitorGroup() {
        return GroupedOpenApi.builder()
                .group("02-告警与监控")
                .pathsToMatch("/alerts/**", "/dashboard/**", "/metric/**")
                .build();
    }

    /** 系统管理分组：认证 / 用户 / 审计日志 */
    @Bean
    public GroupedOpenApi systemGroup() {
        return GroupedOpenApi.builder()
                .group("03-系统管理")
                .pathsToMatch("/auth/**", "/users/**", "/audit-logs/**")
                .build();
    }
}
