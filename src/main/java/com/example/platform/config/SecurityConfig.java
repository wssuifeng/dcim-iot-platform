package com.example.platform.config;

import com.example.platform.security.CustomAccessDeniedHandler;
import com.example.platform.security.CustomAuthenticationEntryPoint;
import com.example.platform.security.JwtAuthenticationFilter;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.SysAuditLogService;
import org.springframework.beans.factory.annotation.Value;
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

    static final String[] ORG_MANAGE_ROLES = {
            PlatformRole.PLATFORM_ADMIN, PlatformRole.ORG_ADMIN
    };
    static final String[] BUSINESS_READ_ROLES = {
            PlatformRole.ORG_ADMIN, PlatformRole.OPERATOR, PlatformRole.VIEWER
    };
    static final String[] BUSINESS_WRITE_ROLES = {
            PlatformRole.ORG_ADMIN, PlatformRole.OPERATOR
    };
    static final String[] BUSINESS_ADMIN_ROLES = {
            PlatformRole.ORG_ADMIN
    };
    static final String[] AUDIT_READ_ROLES = {
            PlatformRole.PLATFORM_ADMIN, PlatformRole.ORG_ADMIN, PlatformRole.OPERATOR
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SysAuditLogService sysAuditLogService;
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource,
                          SysAuditLogService sysAuditLogService,
                          @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
                          @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.sysAuditLogService = sysAuditLogService;
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
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
    public CustomAccessDeniedHandler customAccessDeniedHandler(SysAuditLogService sysAuditLogService) {
        return new CustomAccessDeniedHandler(sysAuditLogService);
    }

    /*
     * 方法功能:配置安全过滤链
     * 规则:
     *   1. 无状态会话（JWT无状态认证，不使用Session）
     *   2. CORS跨域支持
     *   3. CSRF禁用（JWT无状态，无需CSRF防护）
     *   4. 公开端点：登录
     *   5. PLATFORM_ADMIN：平台级管理与监控入口
     *   6. ORG_ADMIN/OPERATOR：组织业务写入
     *   7. ORG_ADMIN/OPERATOR/VIEWER：组织业务只读
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
                        .accessDeniedHandler(customAccessDeniedHandler(sysAuditLogService)))
                .authorizeHttpRequests(auth -> {
                        // 公开端点：登录接口无需认证
                        auth.requestMatchers("/auth/login").permitAll();
                        // 公开端点：Actuator 健康检查与 SpringDoc 文档（开发/内网使用）
                        auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                        if (swaggerUiEnabled) {
                            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll();
                        }
                        if (apiDocsEnabled) {
                            auth.requestMatchers("/v3/api-docs/**").permitAll();
                        }
                        // 平台/组织管理员：用户管理接口（后续由数据范围服务细化到组织）
                        auth.requestMatchers("/users/**").hasAnyRole(ORG_MANAGE_ROLES);
                        // 平台/组织管理员：Druid监控和审计清理
                        auth.requestMatchers("/druid/**").hasAnyRole(ORG_MANAGE_ROLES);
                        auth.requestMatchers(HttpMethod.DELETE, "/audit-logs/**").hasAnyRole(ORG_MANAGE_ROLES);
                        // 平台/组织管理员/运维：审计日志查询
                        auth.requestMatchers("/audit-logs/**").hasAnyRole(AUDIT_READ_ROLES);
                        // 组织管理员：业务删除操作
                        auth.requestMatchers(HttpMethod.DELETE).hasAnyRole(BUSINESS_ADMIN_ROLES);
                        // 组织管理员/运维：设备写入、数据上报、指标创建
                        auth.requestMatchers(HttpMethod.POST, "/devices", "/device-data",
                                "/device-data/batch", "/metric").hasAnyRole(BUSINESS_WRITE_ROLES);
                        // 组织管理员/运维：设备更新、设备类型更新、指标更新
                        auth.requestMatchers(HttpMethod.PUT, "/devices/**",
                                "/device-types/**", "/metric").hasAnyRole(BUSINESS_WRITE_ROLES);
                        // 组织管理员/运维：告警处理（VIEWER不可处理告警）
                        auth.requestMatchers(HttpMethod.PUT, "/alerts/**").hasAnyRole(BUSINESS_WRITE_ROLES);
                        // 组织业务只读接口：PLATFORM_ADMIN 不默认穿透组织业务数据
                        auth.requestMatchers(HttpMethod.GET,
                                "/devices/**", "/device-data/**", "/device-types/**",
                                "/alerts/**", "/dashboard/**", "/metric/**")
                                .hasAnyRole(BUSINESS_READ_ROLES);
                        // 其他GET仅要求认证，具体业务范围由后续服务层校验
                        auth.requestMatchers(HttpMethod.GET).authenticated();
                        // 兜底规则：其余请求需认证
                        auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
