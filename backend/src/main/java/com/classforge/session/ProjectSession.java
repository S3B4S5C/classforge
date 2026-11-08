package com.classforge.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectSession(UUID id, UUID projectId, Instant startedAt, Instant endedAt,
        long startRevision, long endRevision, String summary, ProjectSessionStatus status,
        Instant createdAt, Instant updatedAt, Instant lastActivityAt, String closeReason,
        long totalOperations, long totalChanges, Map<String,Long> operationCounts,
        List<Participant> participants) {
    public record Participant(UUID userId, String displayName, long operations, long changes) { }
}
