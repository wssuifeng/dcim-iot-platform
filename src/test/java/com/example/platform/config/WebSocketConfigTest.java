package com.example.platform.config;

import com.example.platform.security.WebSocketAuthenticationInterceptor;
import com.example.platform.security.WebSocketDestinations;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void configureClientInboundChannelRegistersAuthenticationInterceptor() {
        WebSocketAuthenticationInterceptor interceptor =
                mock(WebSocketAuthenticationInterceptor.class);
        ChannelRegistration registration = mock(ChannelRegistration.class);
        WebSocketConfig config = new WebSocketConfig(
                List.of("http://localhost:5173"), interceptor);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(interceptor);
    }

    @Test
    void configureMessageBrokerUsesOnlyUserIsolatedQueue() {
        WebSocketAuthenticationInterceptor interceptor =
                mock(WebSocketAuthenticationInterceptor.class);
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration brokerRegistration = mock(SimpleBrokerRegistration.class);
        when(registry.enableSimpleBroker(WebSocketDestinations.BROKER_PREFIX))
                .thenReturn(brokerRegistration);
        WebSocketConfig config = new WebSocketConfig(
                List.of("http://localhost:5173"), interceptor);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker(WebSocketDestinations.BROKER_PREFIX);
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix(WebSocketDestinations.USER_PREFIX);
        verify(registry, never()).enableSimpleBroker("/topic");
    }
}
