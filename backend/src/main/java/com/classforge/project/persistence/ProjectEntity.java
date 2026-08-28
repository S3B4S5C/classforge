package com.classforge.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private long revision;

    /*
     * Se conserva el nombre fisico uml_model para no destruir bases H2
     * existentes. Desde CU-02 el contenido representa ProjectDocument.
     */
    @Lob
    @Column(name = "uml_model", nullable = false)
    private String documentJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEntity() {
    }

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