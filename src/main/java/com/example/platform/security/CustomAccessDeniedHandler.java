package com.example.platform.security;

import com.example.platform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/*
 * 类功能:权限不足处理器，当已认证用户访问超出其角色权限的资源时返回403 JSON响应
 *
 * 注意：此类不使用@Component注册，由SecurityConfig通过@Bean方法创建实例，
 * 避免ObjectMapper构造注入导致的循环依赖（SecurityConfig → Handler → ObjectMapper → SecurityConfig）
 * */
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler() {
        this.objectMapper = new ObjectMapper();
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

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.error(403, "权限不足，无法访问该资源");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
