package com.classforge.project.application;

import com.classforge.project.persistence.ProjectInvitationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProjectInvitationView(
        UUID id,
        UUID projectId,
        String projectName,
        String invitedEmail,
        UUID invitedByUserId,
        String invitedByDisplayName,
        ProjectInvitationStatus status,
        Instant createdAt,
        Instant respondedAt
) {
    public ProjectInvitationView {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(projectName, "projectName is required");
        Objects.requireNonNull(invitedEmail, "invitedEmail is required");
        Objects.requireNonNull(invitedByUserId, "invitedByUserId is required");
        Objects.requireNonNull(invitedByDisplayName, "invitedByDisplayName is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
