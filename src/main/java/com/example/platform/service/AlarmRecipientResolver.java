package com.example.platform.service;

import java.util.Set;

/**
 * Resolves users eligible to receive an alarm for an exact organization and cloud room.
 */
public interface AlarmRecipientResolver {

    Set<Long> resolveRecipientUserIds(Long organizationId, Long cloudRoomId);
}
