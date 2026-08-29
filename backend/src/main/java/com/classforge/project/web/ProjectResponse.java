package com.classforge.project.web;

import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.application.AccessibleProject;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        ProjectAccessRole accessRole,
        long revision,
        ProjectDocument document,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(
            Project project,
            ProjectAccessRole accessRole
    ) {
        return new ProjectResponse(
                project.id(),
                project.name(),
                accessRole,
                project.revision(),
                project.document(),
                project.createdAt(),
                project.updatedAt()
        );
    }

    public static ProjectResponse from(
            AccessibleProject accessibleProject
    ) {
        return from(
                accessibleProject.project(),
                accessibleProject.accessRole()
        );
    }
}
