package com.example.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/*
 * 类功能:CORS跨域配置类，通过配置文件动态读取允许的源，支持生产环境灵活部署
 * */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private List<String> allowedOrigins;

    /*
     * 方法功能:创建并注册CORS过滤器Bean
     * 参数:无
     * 返回数据:CorsFilter-CORS过滤器实例
     * */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = buildCorsConfiguration();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    /*
     * 方法功能:构建CORS配置对象，设置允许的源、方法、请求头及凭证策略
     * 参数:无
     * 返回数据:CorsConfiguration-CORS配置对象
     * */
    private CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();

        // 从配置文件读取允许的源，支持通配符模式
        for (String origin : allowedOrigins) {
            config.addAllowedOriginPattern(origin);
        }

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        return config;
    }
}
