package com.classforge.project.application;

import java.util.UUID;

public class ProjectInvitationNotFoundException extends RuntimeException {

    public ProjectInvitationNotFoundException(UUID invitationId) {
        super("Project invitation not found: " + invitationId);
    }
}
