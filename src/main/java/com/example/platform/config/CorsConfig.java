package com.example.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        // 直接调用提取出来的方法获取 config，职责更清晰
        CorsConfiguration config = buildCorsConfiguration();


        // 7. 创建基于 URL 的 CORS 配置源，用于将配置应用到指定路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 8. 对所有路径（"/**" 表示所有接口）应用上述 CORS 配置
        source.registerCorsConfiguration("/**", config);

        // 9. 返回 CORS 过滤器实例，注册到 Spring 过滤器链中
        return new CorsFilter(source);
    }

    // 新提取的私有方法：专门负责构建 CorsConfiguration
    private CorsConfiguration buildCorsConfiguration() {
        // 1. 创建 CORS 配置对象，用于设置跨域规则
        CorsConfiguration config = new CorsConfiguration();

        // 2. 允许访问的源（Origin）
        // 使用 addAllowedOriginPattern 支持通配符，配合 allowCredentials(true) 在 Spring 5.3+ 中可用
        // 允许 localhost 任意端口的前端应用访问（如 http://localhost:3000, http://localhost:8080 等）
        config.addAllowedOriginPattern("http://localhost:*");

        // 允许 127.0.0.1 任意端口访问（某些环境或旧版浏览器可能使用 127.0.0.1 而非 localhost）
        config.addAllowedOriginPattern("http://127.0.0.1:*");

        // 3. 允许的 HTTP 请求方法
        // "*" 表示允许所有：GET, POST, PUT, DELETE, OPTIONS, PATCH 等
        config.addAllowedMethod("*");

        // 4. 允许的请求头
        // "*" 表示允许携带任何自定义请求头（如 Authorization、Content-Type、X-Requested-With 等）
        config.addAllowedHeader("*");

        // 5. 是否允许携带凭证（cookies、authorization headers、TLS client certificates）
        // true 表示允许前端发送包含凭证的请求（如携带 JSESSIONID 或自定义 Token 的 Cookie）
        // 注意：如果使用 addAllowedOrigin("*") 与 allowCredentials(true) 组合在旧版 Spring 中会报错，
        // 这里使用 addAllowedOriginPattern 是安全的写法
        config.setAllowCredentials(true);

        // 6. 预检请求（OPTIONS）的缓存时间，单位：秒
        // 浏览器在发送正式请求前会先发送 OPTIONS 预检请求
        // 设置 3600 秒后，浏览器在 1 小时内对同一请求不再重复预检，提升性能
        config.setMaxAge(3600L);

        return config;
    }
}