package com.example.platform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

/*
 * 类功能:认证 STOMP CONNECT，并限制客户端只能订阅用户隔离的最小摘要通道
 * */
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAuthenticationService jwtAuthenticationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            requireAuthenticated(accessor.getUser());
            requireSummaryDestination(accessor.getDestination());
            return message;
        }

        if (StompCommand.SEND.equals(command)) {
            requireAuthenticated(accessor.getUser());
            throw new AccessDeniedException("WebSocket最小摘要通道不接受客户端写入");
        }

        if (StompCommand.UNSUBSCRIBE.equals(command)) {
            requireAuthenticated(accessor.getUser());
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header)) {
            header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER.toLowerCase());
        }
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "WebSocket CONNECT缺少Bearer Token");
        }
        return jwtAuthenticationService.authenticate(header.substring(BEARER_PREFIX.length()));
    }

    private void requireAuthenticated(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("WebSocket会话未认证");
        }
    }

    private void requireSummaryDestination(String destination) {
        if (!WebSocketDestinations.SUMMARY_SUBSCRIPTION.equals(destination)) {
            throw new AccessDeniedException("WebSocket订阅目的地不被允许");
        }
    }
}
