package com.classforge.project.persistence;

import com.classforge.project.access.ProjectAccessRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "project_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_membership_project_user",
                        columnNames = {"project_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_project_membership_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_project_membership_project",
                        columnList = "project_id"
                )
        }
)
public class ProjectMembershipEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectAccessRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
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
