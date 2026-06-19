package com.example.platform.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * 类功能:XSS 过滤器，对所有请求参数进行 HTML 转义，防御反射型 XSS 攻击
 *
 * 执行顺序：在 TraceIdFilter 之后、RateLimitFilter 之前执行，
 *          确保请求参数进入 Controller 前已完成清洗。
 * */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class XssFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new XssRequestWrapper(request), response);
    }
}
