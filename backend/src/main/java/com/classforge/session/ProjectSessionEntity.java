package com.classforge.session;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("project_sessions")
public class ProjectSessionEntity {
    @Id private UUID id;
    private UUID projectId;
    // A partial unique MongoDB index on projectId/status enforces a single OPEN session.
    private UUID openProjectId;
    private Instant startedAt;
    private Instant endedAt;
    private long startRevision;
    private long endRevision;
    private String summary;
    private ProjectSessionStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastActivityAt;
    private String closeReason;
    private long totalOperations;
    private long totalChanges;
    private String operationCountsJson;

    protected ProjectSessionEntity() { }
    public ProjectSessionEntity(UUID projectId, long revision, Instant now) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.openProjectId = projectId;
        this.startedAt = now; this.createdAt = now; this.updatedAt = now; this.lastActivityAt = now;
        this.startRevision = revision; this.endRevision = revision; this.summary = "";
        this.status = ProjectSessionStatus.OPEN; this.operationCountsJson = "{}";
    }
    public void record(long revision, Instant now, int changes, String countsJson) {
        endRevision = revision; lastActivityAt = now; updatedAt = now;
        totalOperations++; totalChanges += changes; operationCountsJson = countsJson;
    }
    public void close(Instant at, String reason, Instant now) {
        status = ProjectSessionStatus.CLOSED; openProjectId = null;
        endedAt = at; closeReason = reason; updatedAt = now;
    }
    public void summarize(String text) { summary = text; }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public long getStartRevision() { return startRevision; }
    public long getEndRevision() { return endRevision; }
    public String getSummary() { return summary; }
    public ProjectSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCloseReason() { return closeReason; }
    public long getTotalOperations() { return totalOperations; }
    public long getTotalChanges() { return totalChanges; }
    public String getOperationCountsJson() { return operationCountsJson; }
}
