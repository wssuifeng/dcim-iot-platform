package com.example.platform.ingest.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/*
 * 类功能:请求体缓存包装器，将原始报文字节读入内存供网关鉴权过滤器验签后重复读取
 *
 * 网关签名覆盖 rawBody，必须先于反序列化读取一次原始字节验签，
 * 再以本包装器把同一字节流交给后续过滤器和控制器。
 * */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    /*
     * 方法功能:以已读取的原始报文字节构造包装器
     * 参数:request-原始请求, body-原始报文字节
     * 返回数据:无
     * */
    public CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 同步读取场景无需异步监听
            }

            @Override
            public int read() {
                return buffer.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }
}
