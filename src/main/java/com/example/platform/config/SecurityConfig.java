package com.example.platform.config;

import com.example.platform.ingest.http.GatewayAuthenticationFilter;
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
 * RBAC权限层级（单租户三角色）: ADMIN > OPERATOR > VIEWER
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

    static final String[] ADMIN_ONLY_ROLES = {PlatformRole.ADMIN};
    static final String[] BUSINESS_READ_ROLES = {
            PlatformRole.ADMIN, PlatformRole.OPERATOR, PlatformRole.VIEWER
    };
    static final String[] BUSINESS_WRITE_ROLES = {
            PlatformRole.ADMIN, PlatformRole.OPERATOR
    };
    static final String[] AUDIT_READ_ROLES = {
            PlatformRole.ADMIN, PlatformRole.OPERATOR
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SysAuditLogService sysAuditLogService;
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          GatewayAuthenticationFilter gatewayAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource,
                          SysAuditLogService sysAuditLogService,
                          @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
                          @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
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
     * 参数:sysAuditLogService-审计日志服务
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
     *   4. 公开端点：登录/注册
     *   5. ADMIN：用户管理 + 设备类型写 + Druid监控 + 审计清理
     *   6. ADMIN/OPERATOR：业务写入
     *   7. 全部已认证用户：业务只读
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
                        // 公开端点：登录与注册接口无需认证
                        auth.requestMatchers("/auth/login", "/auth/register",
                                "/auth/registration-codes").permitAll();
                        // 公开端点：Actuator 健康检查与 SpringDoc 文档（开发/内网使用）
                        auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                        // 数据接收门：网关机器鉴权（GatewayAuthenticationFilter 验签，无效 401）
                        auth.requestMatchers("/ingest/**").permitAll();
                        // 接入管理面：网关凭证 CRUD 全部 ADMIN（@PreAuthorize 兜底）
                        auth.requestMatchers("/ingest-credentials/**").hasAnyRole(ADMIN_ONLY_ROLES);
                        if (swaggerUiEnabled) {
                            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll();
                        }
                        if (apiDocsEnabled) {
                            auth.requestMatchers("/v3/api-docs/**").permitAll();
                        }
                        // 管理员：用户管理接口
                        auth.requestMatchers("/users/**").hasAnyRole(ADMIN_ONLY_ROLES);
                        // 管理员：Druid监控和审计清理
                        auth.requestMatchers("/druid/**").hasAnyRole(ADMIN_ONLY_ROLES);
                        auth.requestMatchers(HttpMethod.DELETE, "/audit-logs/**").hasAnyRole(ADMIN_ONLY_ROLES);
                        // 管理员/运维：审计日志查询
                        auth.requestMatchers("/audit-logs/**").hasAnyRole(AUDIT_READ_ROLES);
                        // 设备类型是全局目录，写操作仅允许 ADMIN。
                        auth.requestMatchers(HttpMethod.POST, "/device-types", "/device-types/**")
                                .hasAnyRole(ADMIN_ONLY_ROLES);
                        auth.requestMatchers(HttpMethod.PUT, "/device-types/**")
                                .hasAnyRole(ADMIN_ONLY_ROLES);
                        auth.requestMatchers(HttpMethod.DELETE, "/device-types/**")
                                .hasAnyRole(ADMIN_ONLY_ROLES);
                        // 管理员：其他业务删除操作
                        auth.requestMatchers(HttpMethod.DELETE).hasAnyRole(ADMIN_ONLY_ROLES);
                        // 管理员/运维：设备/点位/采样写入（其余写接口由 @PreAuthorize 细化）
                        auth.requestMatchers(HttpMethod.POST, "/devices", "/metric-samples",
                                "/metric-samples/batch", "/point-definitions",
                                "/point-alarm-rules").hasAnyRole(BUSINESS_WRITE_ROLES);
                        auth.requestMatchers(HttpMethod.PUT, "/devices/**",
                                "/point-definitions/**", "/point-templates/**",
                                "/point-alarm-rules/**", "/metric-samples/**")
                                .hasAnyRole(BUSINESS_WRITE_ROLES);
                        // 管理员/运维：告警处理（VIEWER不可处理告警）
                        auth.requestMatchers(HttpMethod.PUT, "/alarm-events/**").hasAnyRole(BUSINESS_WRITE_ROLES);
                        // 业务只读接口：全部已认证角色
                        auth.requestMatchers(HttpMethod.GET,
                                "/devices/**", "/metric-samples/**", "/metric-latest/**",
                                "/point-definitions/**", "/point-templates/**",
                                "/point-alarm-rules/**", "/alarm-events/**",
                                "/alarm-notification-logs/**",
                                "/dashboard/**").hasAnyRole(BUSINESS_READ_ROLES);
                        // 其他GET仅要求认证
                        auth.requestMatchers(HttpMethod.GET).authenticated();
                        // 兜底规则：其余请求需认证
                        auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // 网关机器鉴权只拦 /ingest/**，与用户 JWT 链互不干扰
                .addFilterBefore(gatewayAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
