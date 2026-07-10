package com.example.platform.config;

import com.example.platform.security.WebSocketAuthenticationInterceptor;
import com.example.platform.security.WebSocketDestinations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/*
 * 类功能:STOMP WebSocket 配置，支持实时告警推送
 *
 * 端点：/ws
 * 用户订阅：/user/queue/summary
 * 应用前缀：/app
 * */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOrigins;
    private final WebSocketAuthenticationInterceptor authenticationInterceptor;

    public WebSocketConfig(
            @Value("${websocket.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
            List<String> allowedOrigins,
            WebSocketAuthenticationInterceptor authenticationInterceptor) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker(WebSocketDestinations.BROKER_PREFIX);
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix(WebSocketDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }
}
