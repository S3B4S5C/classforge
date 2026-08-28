package com.classforge.collaboration.security;

import com.classforge.project.persistence.ProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;

    public ProjectAccessService(
            ProjectRepository projectRepository
    ) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public void requireAccess(
            UUID userId,
            UUID projectId
    ) {
        if (
                projectRepository
                        .findByIdAndOwnerId(
                                projectId,
                                userId
                        )
                        .isEmpty()
        ) {
            throw new AccessDeniedException(
                    "The authenticated user cannot access this project"
            );
        }
    }
}