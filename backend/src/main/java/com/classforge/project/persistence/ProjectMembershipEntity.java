package com.classforge.project.persistence;

import com.classforge.project.access.ProjectAccessRole;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Document("project_memberships")
public class ProjectMembershipEntity {

    @Id
    private UUID id;

    private UUID projectId;

    private UUID userId;

    private ProjectAccessRole role;

    private Instant createdAt;

    protected ProjectMembershipEntity() {
    }

    public ProjectMembershipEntity(
            UUID id,
            UUID projectId,
            UUID userId,
            ProjectAccessRole role,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.role = Objects.requireNonNull(role, "role is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");

        if (role != ProjectAccessRole.EDITOR) {
            throw new IllegalArgumentException(
                    "Persisted memberships currently support EDITOR only"
            );
        }
    }

    public static ProjectMembershipEntity editor(
            UUID projectId,
            UUID userId
    ) {
        return new ProjectMembershipEntity(
                UUID.randomUUID(),
                projectId,
                userId,
                ProjectAccessRole.EDITOR,
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProjectAccessRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
