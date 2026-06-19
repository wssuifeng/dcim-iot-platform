package com.example.platform.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

/*
 * 类功能:XSS 请求包装器，对查询参数和表单参数进行 HTML 转义
 *
 * 说明：
 *   - 仅转义 getParameter / getParameterValues 返回的字符串
 *   - 请求头默认不转义，避免破坏 User-Agent、Authorization 等标准头
 *   - application/json 请求体由业务层自行校验或输出时转义，不在此处处理
 * */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return escape(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] escaped = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            escaped[i] = escape(values[i]);
        }
        return escaped;
    }

    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return HtmlUtils.htmlEscape(value);
    }
}
