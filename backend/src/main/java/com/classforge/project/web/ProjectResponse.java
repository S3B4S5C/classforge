package com.classforge.project.web;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        long revision,
        ProjectDocument document,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.id(),
                project.name(),
                project.revision(),
                project.document(),
                project.createdAt(),
                project.updatedAt()
        );
    }
}