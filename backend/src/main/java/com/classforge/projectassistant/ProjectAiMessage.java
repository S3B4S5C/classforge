package com.classforge.projectassistant;

import com.classforge.assistant.AssistantPlanResponse;
import java.time.Instant;
import java.util.UUID;

public record ProjectAiMessage(UUID id, long sequence, UUID projectId, UUID requestId,
        UUID authorUserId, String authorDisplayName, String role, AssistantMode type, String content,
        long projectRevision, Instant createdAt, AssistantReview review, AssistantPlanResponse plan) { }
