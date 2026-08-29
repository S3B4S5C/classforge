package com.classforge.project.application;

import com.classforge.project.access.ProjectAccessRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProjectCollaboratorView(
        UUID userId,
        String displayName,
        String email,
        ProjectAccessRole accessRole,
        Instant joinedAt
) {
    public ProjectCollaboratorView {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(accessRole, "accessRole is required");
        Objects.requireNonNull(joinedAt, "joinedAt is required");
    }
}
