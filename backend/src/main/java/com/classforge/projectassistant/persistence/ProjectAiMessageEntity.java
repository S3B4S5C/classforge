package com.classforge.projectassistant.persistence;

import com.classforge.assistant.AssistantPlanResponse;
import com.classforge.projectassistant.*;
import jakarta.persistence.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_ai_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_project_request_role", columnNames = {"project_id", "request_id", "message_role"}),
        @UniqueConstraint(name = "uk_ai_message_id", columnNames = "message_id")
}, indexes = @Index(name = "idx_ai_project_sequence", columnList = "project_id,message_sequence"))
public class ProjectAiMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_sequence") private Long sequence;
    @Column(name = "message_id", nullable = false, updatable = false) private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "request_id", nullable = false, updatable = false) private UUID requestId;
    @Column(name = "author_user_id", updatable = false) private UUID authorUserId;
    @Column(name = "author_display_name", nullable = false, length = 80) private String authorDisplayName;
    @Column(name = "message_role", nullable = false, length = 16) private String role;
    @Enumerated(EnumType.STRING) @Column(name = "message_type", nullable = false, length = 24) private AssistantMode type;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "project_revision", nullable = false) private long projectRevision;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "review_json", columnDefinition = "text") private String reviewJson;
    @Column(name = "plan_json", columnDefinition = "text") private String planJson;
    @Column(name = "generation_status", length = 16) private String generationStatus;
    @Column(name = "generation_token") private UUID generationToken;
    @Column(name = "generation_updated_at") private Instant generationUpdatedAt;

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
        // H2/PostgreSQL persist microseconds; publish the same timestamp returned on replay.
        entity.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return entity;
    }

    public ProjectAiMessage toMessage(JsonMapper mapper) {
        return new ProjectAiMessage(id, sequence, projectId, requestId, authorUserId, authorDisplayName,
                role, type, content, projectRevision, createdAt,
                reviewJson == null ? null : mapper.readValue(reviewJson, AssistantReview.class),
                planJson == null ? null : mapper.readValue(planJson, AssistantPlanResponse.class));
    }

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
