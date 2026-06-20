package com.example.platform.config;

import com.example.platform.security.CustomAccessDeniedHandler;
import com.example.platform.security.CustomAuthenticationEntryPoint;
import com.example.platform.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/*
 * 类功能:Spring Security核心配置类，定义JWT过滤链、端点访问规则、RBAC鉴权策略
 *
 * RBAC权限层级: ADMIN > OPERATOR > VIEWER
 * - ADMIN:    全部操作 + 用户管理 + 删除操作 + Druid监控 + 审计日志清理
 * - OPERATOR: 设备管理 + 告警处理 + 数据上报 + 只读查询
 * - VIEWER:   只读查询（设备/数据/告警/日志）
 *
 * 注意：CustomAuthenticationEntryPoint和CustomAccessDeniedHandler通过@Bean方法在此类中创建，
 * 而非@Component自动扫描，避免ObjectMapper构造注入导致的循环依赖
 * */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /*
     * 方法功能:创建BCrypt密码编码器Bean
     * 参数:无
     * 返回数据:PasswordEncoder-BCrypt密码编码器实例
     * */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * 方法功能:创建未认证入口点Bean（手动实例化，避免循环依赖）
     * 参数:无
     * 返回数据:CustomAuthenticationEntryPoint-未认证入口点实例
     * */
    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new CustomAuthenticationEntryPoint();
    }

    /*
     * 方法功能:创建权限不足处理器Bean（手动实例化，避免循环依赖）
     * 参数:无
     * 返回数据:CustomAccessDeniedHandler-权限不足处理器实例
     * */
    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    /*
     * 方法功能:配置安全过滤链
     * 规则:
     *   1. 无状态会话（JWT无状态认证，不使用Session）
     *   2. CORS跨域支持
     *   3. CSRF禁用（JWT无状态，无需CSRF防护）
     *   4. 公开端点：登录
     *   5. ADMIN专属：用户管理、删除操作、Druid监控、审计日志清理
     *   6. OPERATOR及以上：设备写入、数据上报、告警配置、指标/设备类型管理
     *   7. 已认证用户：只读查询
     *   8. JWT过滤器插入UsernamePasswordAuthenticationFilter之前
     * */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        // 公开端点：登录接口无需认证
                        .requestMatchers("/auth/login").permitAll()
                        // 公开端点：Actuator 健康检查与 SpringDoc 文档（开发/内网使用）
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // ADMIN专属：Druid监控页面
                        .requestMatchers("/druid/**").hasRole("ADMIN")
                        // ADMIN专属：所有DELETE操作（含审计日志清理）
                        .requestMatchers(HttpMethod.DELETE).hasRole("ADMIN")
                        // ADMIN专属：用户管理全部接口
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        // ADMIN/OPERATOR：审计日志查询（DELETE由上方规则单独控制）
                        .requestMatchers("/audit-logs/**").hasAnyRole("ADMIN", "OPERATOR")
                        // ADMIN/OPERATOR：设备写入、数据上报、指标创建
                        .requestMatchers(HttpMethod.POST, "/devices", "/device-data",
                                "/device-data/batch", "/metric").hasAnyRole("ADMIN", "OPERATOR")
                        // ADMIN/OPERATOR：设备更新、设备类型更新、指标更新
                        .requestMatchers(HttpMethod.PUT, "/devices/**",
                                "/device-types/**", "/metric").hasAnyRole("ADMIN", "OPERATOR")
                        // ADMIN/OPERATOR：告警处理（VIEWER不可处理告警）
                        .requestMatchers(HttpMethod.PUT, "/alerts/**").hasAnyRole("ADMIN", "OPERATOR")
                        // 已认证用户：所有GET只读查询（VIEWER可访问）
                        .requestMatchers(HttpMethod.GET).authenticated()
                        // 兜底规则：其余请求需认证
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
