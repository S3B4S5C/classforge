package com.classforge.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("project_notifications")
public record ProjectNotification(@Id UUID id, UUID projectId, UUID recipientUserId,
        Type type, String title, String message, UUID activityId, UUID sessionId,
        String dedupKey, boolean read, Instant createdAt, Instant readAt) {
    public enum Type { PROJECT_ACTIVITY, SESSION_CLOSED, MENTION, SYSTEM }
}
