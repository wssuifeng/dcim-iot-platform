package com.example.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthenticationInterceptorTest {

    @Mock
    private JwtAuthenticationService jwtAuthenticationService;

    @InjectMocks
    private WebSocketAuthenticationInterceptor interceptor;

    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectAuthenticatesBearerTokenAndSetsPrincipal() {
        Authentication authentication = authentication(42L, PlatformRole.OPERATOR);
        when(jwtAuthenticationService.authenticate("valid-token")).thenReturn(authentication);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = message(accessor);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isSameAs(authentication);
    }

    @Test
    void subscribeRejectsUnauthenticatedSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(WebSocketDestinations.SUMMARY_SUBSCRIPTION);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void subscribeAllowsAuthenticatedUserSummaryQueue() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication(42L, PlatformRole.VIEWER));
        accessor.setDestination(WebSocketDestinations.SUMMARY_SUBSCRIPTION);
        Message<byte[]> message = message(accessor);

        assertThatCode(() -> interceptor.preSend(message, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void subscribeRejectsLegacyGlobalTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication(42L, PlatformRole.ADMIN));
        accessor.setDestination("/topic/alerts");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    void sendRejectsClientWrites() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(authentication(42L, PlatformRole.OPERATOR));
        accessor.setDestination("/app/alerts");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("不接受客户端写入");
    }

    private Authentication authentication(Long userId, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId, null, PlatformRole.authoritiesFor(role));
        authentication.setDetails("user-" + userId);
        return authentication;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
