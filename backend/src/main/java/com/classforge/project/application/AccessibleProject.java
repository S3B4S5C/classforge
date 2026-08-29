package com.classforge.project.application;

import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.domain.Project;

import java.util.Objects;

public record AccessibleProject(
        Project project,
        ProjectAccessRole accessRole
) {
    public AccessibleProject {
        Objects.requireNonNull(project, "project is required");
        Objects.requireNonNull(accessRole, "accessRole is required");

        if (!accessRole.canRead()) {
            throw new IllegalArgumentException(
                    "Accessible project cannot use NONE role"
            );
        }
    }
}
