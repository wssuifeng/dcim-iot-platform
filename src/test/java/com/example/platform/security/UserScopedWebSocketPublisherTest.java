package com.example.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserScopedWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UserScopedWebSocketPublisher publisher;

    @Test
    void sendSummaryTargetsOnlySpecifiedUserQueue() {
        WebSocketSummary summary = new WebSocketSummary(
                "ALARM_STATE_CHANGED", "ALARM", "123", "ACKED", Instant.now());

        publisher.sendSummary(42L, summary);

        verify(messagingTemplate).convertAndSendToUser(
                "42", WebSocketDestinations.SUMMARY_QUEUE, summary);
    }

    @Test
    void sendSummaryRejectsMissingUser() {
        WebSocketSummary summary = new WebSocketSummary(
                "ALARM_STATE_CHANGED", "ALARM", "123", "ACKED", Instant.now());

        assertThatThrownBy(() -> publisher.sendSummary(null, summary))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
