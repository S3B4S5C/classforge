package com.classforge.activity;

import java.time.Instant;
import java.util.UUID;

public record ProjectActivity(UUID id, UUID projectId, UUID operationId, UUID actorUserId,
        String actorDisplayName, long revisionBefore, long revisionAfter,
        ActivityOperationType operationType, ActivitySource source, String description,
        String payloadJson, UUID sessionId, Instant createdAt) { }
