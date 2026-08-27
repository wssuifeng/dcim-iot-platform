package com.example.platform.ingest.http;

import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/*
 * 类功能:网关机器鉴权过滤器，仅拦截 /ingest/** 上报路径
 *
 * 【鉴权协议】Authorization: Gateway {accessKey}:{sha256(secret + rawBody)}
 *   签名覆盖原始报文字节：先读 body 验签，再以 CachedBodyRequestWrapper 交给后续链。
 * 【失败语义】无效/停用/过期凭证或签名不匹配 → 401 + INGEST_UNAUTHORIZED(4095)，
 *   响应体与用户态 401(4010) 区分，便于网关侧区分重试语义。
 * 【身份传递】验签通过后将凭证实体写入请求属性 ATTR_CREDENTIAL，控制器直接取用。
 * */
@Slf4j
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String GATEWAY_PREFIX = "Gateway ";
    // 仅拦蔽数据接收门路径，凭证管理(/ingest-credentials)走用户 JWT 鉴权
    private static final String INGEST_PATH_PREFIX = "/ingest/";

    public static final String ATTR_CREDENTIAL = "ingest.credential";

    private final IngestCredentialService ingestCredentialService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GatewayAuthenticationFilter(IngestCredentialService ingestCredentialService) {
        this.ingestCredentialService = ingestCredentialService;
    }

    /*
     * 方法功能:解析并校验网关凭证签名，通过后以缓存包装器续传过滤器链
     * 参数:request-HTTP请求, response-HTTP响应, filterChain-过滤器链
     * 返回数据:无
     * */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String accessKey;
        String signature;
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(GATEWAY_PREFIX)) {
            String[] parts = header.substring(GATEWAY_PREFIX.length()).split(":", 2);
            if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                writeUnauthorized(response, "网关鉴权头格式错误，应为 Gateway {accessKey}:{signature}");
                return;
            }
            accessKey = parts[0];
            signature = parts[1];
        } else {
            writeUnauthorized(response, "缺少网关鉴权头 Authorization: Gateway {accessKey}:{signature}");
            return;
        }

        // 原始报文字节只读一次：验签 + 缓存包装后交控制器反序列化
        byte[] rawBody = request.getInputStream().readAllBytes();

        IngestCredential credential;
        try {
            credential = ingestCredentialService.authenticate(accessKey);
        } catch (BusinessException ex) {
            log.warn("网关鉴权失败 accessKey={} reason={}", accessKey, ex.getMessage());
            writeUnauthorized(response, ex.getMessage());
            return;
        }

        if (!GatewaySignUtils.verify(credential.getSecret(), rawBody, signature)) {
            log.warn("网关签名校验失败 accessKey={}", accessKey);
            writeUnauthorized(response, "网关签名校验失败");
            return;
        }

        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request, rawBody);
        wrapped.setAttribute(ATTR_CREDENTIAL, credential);
        filterChain.doFilter(wrapped, response);
    }

    /*
     * 方法功能:仅对 /ingest/** 路径生效，其余请求直接放行
     * 参数:request-HTTP请求
     * 返回数据:boolean-true 表示跳过本过滤器
     * */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 用 requestURI 剥离 contextPath 判定（servletPath 在部分容器/模拟场景为空）
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return !path.startsWith(INGEST_PATH_PREFIX);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(ResultCode.INGEST_UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
