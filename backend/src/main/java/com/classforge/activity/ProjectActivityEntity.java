package com.classforge.activity;

import com.classforge.session.ProjectSessionEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("project_activities")
public class ProjectActivityEntity {
    @Id private UUID id;
    private UUID projectId;
    private UUID operationId;
    private UUID actorUserId;
    private String actorDisplayName;
    private long revisionBefore;
    private long revisionAfter;
    private ActivityOperationType operationType;
    private ActivitySource source;
    private String description;
    private String payloadJson;
    private UUID sessionId;
    private Instant createdAt;

    protected ProjectActivityEntity() { }

    public ProjectActivityEntity(ProjectOperationPersistedEvent event, ProjectSessionEntity session,
            ActivityOperationType type, String description, String payloadJson) {
        this.id = UUID.randomUUID(); this.projectId = event.projectId(); this.operationId = event.operationId();
        this.actorUserId = event.actorUserId(); this.actorDisplayName = event.actorDisplayName();
        this.revisionBefore = event.revisionBefore(); this.revisionAfter = event.revisionAfter();
        this.operationType = type; this.source = event.source(); this.description = description;
        this.payloadJson = payloadJson; this.sessionId = session.getId(); this.createdAt = event.occurredAt();
    }

    public ProjectActivity toActivity() {
        return new ProjectActivity(id, projectId, operationId, actorUserId, actorDisplayName,
                revisionBefore, revisionAfter, operationType, source, description, payloadJson,
                sessionId, createdAt);
    }
}
