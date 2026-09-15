package com.manarah.notification;

import java.time.Instant;

public class NotificationDtos {

    public record NotificationView(
            Long id,
            String title,
            String body,
            String category,
            String channel,
            String status,
            String entityType,
            Long entityId,
            Instant readAt,
            Instant createdAt) {
    }

    /** A command to raise a notification for a single recipient across one or more channels. */
    public record NotifyCommand(
            Long recipientUserId,
            String recipientContact,
            String title,
            String body,
            String category,
            String entityType,
            Long entityId,
            java.util.List<String> channels) {
    }
}
