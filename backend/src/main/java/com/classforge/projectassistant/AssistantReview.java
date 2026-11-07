package com.classforge.projectassistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantReview(UUID id, UUID projectId, long projectRevision, UUID requestedBy,
                              String summary, List<AssistantReviewFinding> findings, Instant createdAt) {
    public AssistantReview { findings = findings == null ? List.of() : List.copyOf(findings); }
}
