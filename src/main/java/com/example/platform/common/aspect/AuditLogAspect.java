package com.example.platform.common.aspect;

import com.example.platform.common.annotation.AuditLog;
import com.example.platform.service.SysAuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/*
 * 类功能:审计日志切面，拦截 @AuditLog 注解的方法，自动记录操作日志
 *
 * 记录时机：方法正常返回后记录成功日志，方法抛出异常后记录失败日志。
 * 用户信息来源：Spring SecurityContext 中的 Authentication。
 * IP 来源：当前请求的 HttpServletRequest，优先读取 X-Forwarded-For 头。
 * */
@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    private final SysAuditLogService sysAuditLogService;

    public AuditLogAspect(SysAuditLogService sysAuditLogService) {
        this.sysAuditLogService = sysAuditLogService;
    }

    @AfterReturning("@annotation(auditLog)")
    public void doAfterReturning(JoinPoint joinPoint, AuditLog auditLog) {
        recordLog(joinPoint, auditLog, null);
    }

    @AfterThrowing(pointcut = "@annotation(auditLog)", throwing = "ex")
    public void doAfterThrowing(JoinPoint joinPoint, AuditLog auditLog, Throwable ex) {
        recordLog(joinPoint, auditLog, ex);
    }

    private void recordLog(JoinPoint joinPoint, AuditLog auditLog, Throwable ex) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            String username = null;
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof Long id) {
                    userId = id;
                }
                Object details = authentication.getDetails();
                if (details instanceof String name) {
                    username = name;
                }
            }

            HttpServletRequest request = getCurrentRequest();
            String ip = request == null ? "unknown" : extractClientIp(request);
            String detail = buildDetail(auditLog.detail(), joinPoint);
            if (ex != null) {
                detail = detail.isEmpty() ? "操作失败: " + ex.getMessage()
                        : detail + " | 操作失败: " + ex.getMessage();
            }

            sysAuditLogService.log(userId, username, auditLog.operation(), ip, detail);
        } catch (Exception e) {
            log.warn("记录审计日志失败: {}", e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
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
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String buildDetail(String template, JoinPoint joinPoint) {
        if (template == null || template.isBlank()) {
            return "";
        }
        // 简单替换方法参数占位符 #arg0、#arg1...
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        String result = template;
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            Object arg = args[i];
            String value = arg == null ? "null" : arg.toString();
            result = result.replace("#" + paramNames[i], value);
        }
        return result;
    }
}
