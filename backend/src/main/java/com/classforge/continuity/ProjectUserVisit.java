package com.classforge.continuity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("project_user_visits")
public record ProjectUserVisit(@Id String id, UUID projectId, UUID userId, Instant lastSeenAt, long lastSeenRevision) {
    public static String key(UUID project, UUID user) { return project + ":" + user; }
}
