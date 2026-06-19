package com.example.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 类功能:SpringDoc OpenAPI 文档基础信息配置
 * */
@Configuration
public class OpenApiConfig {

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
                                .url("https://opensource.org/licenses/MIT")));
    }
}
