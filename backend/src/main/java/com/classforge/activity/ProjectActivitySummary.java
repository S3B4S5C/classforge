package com.classforge.activity;

import java.util.List;
import java.util.UUID;

public record ProjectActivitySummary(UUID projectId, Long fromRevision, Long toRevision,
        long totalChanges, List<Participant> participants) {
    public record Participant(UUID userId, String displayName, long changes) { }
}
