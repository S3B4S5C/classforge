package com.classforge.projectassistant.persistence;

import com.classforge.assistant.AssistantPlanResponse;
import com.classforge.projectassistant.*;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("project_ai_messages")
public class ProjectAiMessageEntity {
    private Long sequence;
    @Id private UUID id;
    private UUID projectId;
    private UUID requestId;
    private UUID authorUserId;
    private String authorDisplayName;
    private String role;
    private AssistantMode type;
    private String content;
    private long projectRevision;
    private Instant createdAt;
    private String reviewJson;
    private String planJson;
    private String generationStatus;
    private UUID generationToken;
    private Instant generationUpdatedAt;

    protected ProjectAiMessageEntity() { }

    public static ProjectAiMessageEntity user(UUID projectId, UUID requestId, UUID userId, String displayName,
            AssistantMode mode, String content, long revision) {
        var entity = base(projectId, requestId, mode, content, revision);
        entity.role = "USER";
        entity.authorUserId = userId;
        entity.authorDisplayName = displayName;
        entity.startGeneration();
        return entity;
    }

    public static ProjectAiMessageEntity assistant(UUID projectId, UUID requestId, AssistantMode mode,
            String content, long revision, AssistantReview review, AssistantPlanResponse plan, JsonMapper mapper) {
        var entity = base(projectId, requestId, mode, content, revision);
        entity.role = "ASSISTANT";
        entity.authorDisplayName = "ClassForge AI";
        entity.reviewJson = review == null ? null : mapper.writeValueAsString(review);
        entity.planJson = plan == null ? null : mapper.writeValueAsString(plan);
        return entity;
    }

    private static ProjectAiMessageEntity base(UUID projectId, UUID requestId, AssistantMode mode, String content, long revision) {
        var entity = new ProjectAiMessageEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.requestId = requestId;
        entity.type = mode;
        entity.content = content;
        entity.projectRevision = revision;
        // Preserve the public timestamp on initial publication and history replay.
        entity.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return entity;
    }

    public ProjectAiMessage toMessage(JsonMapper mapper) {
        return new ProjectAiMessage(id, sequence, projectId, requestId, authorUserId, authorDisplayName,
                role, type, content, projectRevision, createdAt,
                reviewJson == null ? null : mapper.readValue(reviewJson, AssistantReview.class),
                planJson == null ? null : mapper.readValue(planJson, AssistantPlanResponse.class));
    }

    public Long getSequence() { return sequence; }
    public void assignSequence(long sequence) { this.sequence = sequence; }
    public UUID getAuthorUserId() { return authorUserId; }
    public String getContent() { return content; }
    public AssistantMode getType() { return type; }
    public UUID getGenerationToken() { return generationToken; }
    public String getGenerationStatus() { return generationStatus; }
    public Instant getGenerationUpdatedAt() { return generationUpdatedAt; }
    public void startGeneration() {
        generationStatus = "PROCESSING";
        generationToken = UUID.randomUUID();
        generationUpdatedAt = Instant.now();
    }
    public void finishGeneration(String status) { generationStatus = status; generationUpdatedAt = Instant.now(); }
}
