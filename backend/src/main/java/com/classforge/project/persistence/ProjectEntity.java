package com.classforge.project.persistence;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;
import com.classforge.project.domain.document.ProjectDocument;

@Document("projects")
public class ProjectEntity {

    @Id
    private UUID id;

    private UUID ownerId;

    private String name;

    private long revision;

    // Read-only compatibility for legacy imports. New writes persist the native UML document below.
    private String documentJson;
    private ProjectDocument document;

    private Instant createdAt;

    private Instant updatedAt;

    protected ProjectEntity() {
    }

    public ProjectEntity(UUID id, UUID ownerId, String name, long revision, ProjectDocument document,
            Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, revision, (String) null, createdAt, updatedAt);
        this.document = document;
    }

    public ProjectDocument getDocument() { return document; }

    public ProjectEntity(
            UUID id,
            UUID ownerId,
            String name,
            long revision,
            String documentJson,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.revision = revision;
        this.documentJson = documentJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public long getRevision() {
        return revision;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
