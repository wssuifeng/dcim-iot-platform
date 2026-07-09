package com.example.platform.security;

import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.service.SysAuditLogService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.stream.Collectors;

/*
 * 类功能:权限不足处理器，当已认证用户访问超出其角色权限的资源时返回403 JSON响应
 *
 * 注意：此类不使用@Component注册，由SecurityConfig通过@Bean方法创建实例，
 * 避免ObjectMapper构造注入导致的循环依赖（SecurityConfig → Handler → ObjectMapper → SecurityConfig）
 * */
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final SysAuditLogService sysAuditLogService;

    public CustomAccessDeniedHandler(SysAuditLogService sysAuditLogService) {
        this.objectMapper = new ObjectMapper();
        this.sysAuditLogService = sysAuditLogService;
    }

    /*
     * 方法功能:处理权限不足请求，返回标准JSON格式的403错误响应
     * 参数:request-HTTP请求, response-HTTP响应, accessDeniedException-权限不足异常
     * 返回数据:无（直接写入响应流）
     * */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足: {} {}", request.getMethod(), request.getRequestURI());
        recordRejectedAudit(request, accessDeniedException);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.error(ResultCode.FORBIDDEN, "权限不足，无法访问该资源");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private void recordRejectedAudit(HttpServletRequest request, AccessDeniedException exception) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = extractUserId(authentication);
            String username = extractUsername(authentication);
            String authorities = extractAuthorities(authentication);
            String detail = "拒绝访问: " + request.getMethod() + " " + request.getRequestURI()
                    + " | authorities=" + authorities
                    + " | reason=" + exception.getMessage();
            sysAuditLogService.log(userId, username, "ACCESS_DENIED", extractClientIp(request), detail);
        } catch (Exception e) {
            log.warn("记录拒绝审计失败: {}", e.getMessage());
        }
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object details = authentication.getDetails();
        return details instanceof String name ? name : null;
    }

    private String extractAuthorities(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "none";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
