package com.classforge.project.application;

import com.classforge.project.access.ProjectAccessRole;

import java.util.List;
import java.util.Objects;

public record ProjectCollaboratorsView(
        ProjectAccessRole viewerRole,
        ProjectCollaboratorView owner,
        List<ProjectCollaboratorView> editors,
        List<ProjectInvitationView> pendingInvitations
) {
    public ProjectCollaboratorsView {
        Objects.requireNonNull(viewerRole, "viewerRole is required");
        Objects.requireNonNull(owner, "owner is required");
        editors = List.copyOf(editors);
        pendingInvitations = List.copyOf(pendingInvitations);
    }
}
