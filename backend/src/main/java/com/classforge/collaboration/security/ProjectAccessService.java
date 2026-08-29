package com.classforge.collaboration.security;

import com.classforge.project.access.ProjectAccessRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Compatibility adapter for the collaboration layer.
 * The source of truth now lives under com.classforge.project.access.
 */
@Service
public class ProjectAccessService {

    private final com.classforge.project.access.ProjectAccessService
            projectAccessService;

    public ProjectAccessService(
            com.classforge.project.access.ProjectAccessService
                    projectAccessService
    ) {
        this.projectAccessService = projectAccessService;
    }

    public void requireAccess(
            UUID userId,
            UUID projectId
    ) {
        ProjectAccessRole role =
                projectAccessService.role(userId, projectId);

        if (!role.canEdit()) {
            throw new AccessDeniedException(
                    "The authenticated user cannot access this project"
            );
        }
    }
}
