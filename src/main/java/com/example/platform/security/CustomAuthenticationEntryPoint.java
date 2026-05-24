package com.example.platform.security;

import com.example.platform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/*
 * 类功能:未认证入口点，当用户未携带有效Token访问受保护资源时返回401 JSON响应
 *
 * 注意：此类不使用@Component注册，由SecurityConfig通过@Bean方法创建实例，
 * 避免ObjectMapper构造注入导致的循环依赖（SecurityConfig → Handler → ObjectMapper → SecurityConfig）
 * */
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint() {
        this.objectMapper = new ObjectMapper();
    }

    /*
     * 方法功能:处理未认证请求，返回标准JSON格式的401错误响应
     * 参数:request-HTTP请求, response-HTTP响应, authException-认证异常
     * 返回数据:无（直接写入响应流）
     * */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("未认证访问: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.error(401, "未登录或Token已过期");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
