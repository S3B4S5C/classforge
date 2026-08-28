package com.classforge.project.web;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.UmlModelSnapshot;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        long revision,
        UmlModelSnapshot umlModel,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.id(),
                project.name(),
                project.revision(),
                project.umlModel(),
                project.createdAt(),
                project.updatedAt()
        );
    }
}