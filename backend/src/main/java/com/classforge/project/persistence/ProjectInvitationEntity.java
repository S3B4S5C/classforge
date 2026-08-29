package com.classforge.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "project_invitations",
        indexes = {
                @Index(
                        name = "idx_project_invitation_email_status",
                        columnList = "invited_email,status"
                ),
                @Index(
                        name = "idx_project_invitation_project_status",
                        columnList = "project_id,status"
                )
        }
)
public class ProjectInvitationEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "invited_email", nullable = false, length = 160)
    private String invitedEmail;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectInvitationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ProjectInvitationEntity() {
    }

    public ProjectInvitationEntity(
            UUID id,
            UUID projectId,
            String invitedEmail,
            UUID invitedByUserId,
            ProjectInvitationStatus status,
            Instant createdAt,
            Instant respondedAt
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.invitedEmail = requireEmail(invitedEmail);
        this.invitedByUserId = Objects.requireNonNull(invitedByUserId, "invitedByUserId is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.respondedAt = respondedAt;

        if (status == ProjectInvitationStatus.PENDING && respondedAt != null) {
            throw new IllegalArgumentException("Pending invitation cannot have respondedAt");
        }

        if (status != ProjectInvitationStatus.PENDING && respondedAt == null) {
            throw new IllegalArgumentException("Responded invitation requires respondedAt");
        }
    }

    public static ProjectInvitationEntity pending(
            UUID projectId,
            String invitedEmail,
            UUID invitedByUserId
    ) {
        return new ProjectInvitationEntity(
                UUID.randomUUID(),
                projectId,
                invitedEmail,
                invitedByUserId,
                ProjectInvitationStatus.PENDING,
                Instant.now(),
                null
        );
    }

    public void accept() {
        respond(ProjectInvitationStatus.ACCEPTED);
    }

    public void decline() {
        respond(ProjectInvitationStatus.DECLINED);
    }

    public void cancel() {
        respond(ProjectInvitationStatus.CANCELLED);
    }

    private void respond(ProjectInvitationStatus nextStatus) {
        if (status != ProjectInvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be resolved");
        }

        status = Objects.requireNonNull(nextStatus, "nextStatus is required");
        respondedAt = Instant.now();
    }

    private static String requireEmail(String email) {
        Objects.requireNonNull(email, "invitedEmail is required");

        if (email.isBlank()) {
            throw new IllegalArgumentException("invitedEmail cannot be blank");
        }

        return email;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public ProjectInvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
