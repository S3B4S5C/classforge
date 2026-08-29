package com.classforge.project.access;

import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository projectMembershipRepository;

    public ProjectAccessService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository projectMembershipRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMembershipRepository = projectMembershipRepository;
    }

    @Transactional(readOnly = true)
    public ProjectAccessRole role(
            UUID userId,
            UUID projectId
    ) {
        if (projectRepository.existsByIdAndOwnerId(projectId, userId)) {
            return ProjectAccessRole.OWNER;
        }

        return projectMembershipRepository
                .findByProjectIdAndUserId(projectId, userId)
                .filter(membership ->
                        membership.getRole() == ProjectAccessRole.EDITOR
                )
                .map(membership -> ProjectAccessRole.EDITOR)
                .orElse(ProjectAccessRole.NONE);
    }

    @Transactional(readOnly = true)
    public ProjectAccessRole requireRead(
            UUID userId,
            UUID projectId
    ) {
        ProjectAccessRole role = role(userId, projectId);

        if (!role.canRead()) {
            throw new ProjectNotFoundException(projectId);
        }

        return role;
    }

    @Transactional(readOnly = true)
    public ProjectAccessRole requireEdit(
            UUID userId,
            UUID projectId
    ) {
        ProjectAccessRole role = role(userId, projectId);

        if (!role.canEdit()) {
            throw new ProjectNotFoundException(projectId);
        }

        return role;
    }

    @Transactional(readOnly = true)
    public ProjectAccessRole requireOwner(
            UUID userId,
            UUID projectId
    ) {
        ProjectAccessRole role = role(userId, projectId);

        if (!role.isOwner()) {
            throw new ProjectNotFoundException(projectId);
        }

        return role;
    }
}
