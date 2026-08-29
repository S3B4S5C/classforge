package com.classforge.project.application;

import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectEntity;
import com.classforge.project.persistence.ProjectMapper;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.persistence.ProjectRepository;
import com.classforge.project.validation.ProjectDocumentValidationReport;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectDocumentValidator projectDocumentValidator;
    private final ProjectAccessService projectAccessService;
    private final ProjectMembershipRepository projectMembershipRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            ProjectDocumentValidator projectDocumentValidator,
            ProjectAccessService projectAccessService,
            ProjectMembershipRepository projectMembershipRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.projectDocumentValidator = projectDocumentValidator;
        this.projectAccessService = projectAccessService;
        this.projectMembershipRepository = projectMembershipRepository;
    }

    @Transactional
    public Project create(UUID ownerId, String name) {
        Project project = Project.create(ownerId, name);
        return projectMapper.toDomain(
                projectRepository.save(projectMapper.toEntity(project))
        );
    }

    @Transactional(readOnly = true)
    public Project get(UUID userId, UUID projectId) {
        return getAccessible(userId, projectId).project();
    }

    @Transactional(readOnly = true)
    public AccessibleProject getAccessible(
            UUID userId,
            UUID projectId
    ) {
        ProjectAccessRole accessRole =
                projectAccessService.requireRead(userId, projectId);

        Project project = projectRepository
                .findById(projectId)
                .map(projectMapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        return new AccessibleProject(project, accessRole);
    }

    @Transactional(readOnly = true)
    public ProjectAccessRole accessRole(
            UUID userId,
            UUID projectId
    ) {
        return projectAccessService.requireRead(userId, projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> list(UUID ownerId) {
        return projectRepository
                .findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                .stream()
                .map(projectMapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccessibleProject> listAccessible(UUID userId) {
        List<AccessibleProject> result = new ArrayList<>();
        Set<UUID> includedProjectIds = new HashSet<>();

        for (
                ProjectEntity entity :
                projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId)
        ) {
            Project project = projectMapper.toDomain(entity);
            result.add(
                    new AccessibleProject(
                            project,
                            ProjectAccessRole.OWNER
                    )
            );
            includedProjectIds.add(project.id());
        }

        List<UUID> membershipProjectIds =
                projectMembershipRepository
                        .findAllByUserId(userId)
                        .stream()
                        .filter(membership ->
                                membership.getRole()
                                        == ProjectAccessRole.EDITOR
                        )
                        .map(ProjectMembershipEntity::getProjectId)
                        .filter(projectId ->
                                !includedProjectIds.contains(projectId)
                        )
                        .toList();

        if (!membershipProjectIds.isEmpty()) {
            for (
                    ProjectEntity entity :
                    projectRepository.findAllById(membershipProjectIds)
            ) {
                Project project = projectMapper.toDomain(entity);
                result.add(
                        new AccessibleProject(
                                project,
                                ProjectAccessRole.EDITOR
                        )
                );
            }
        }

        result.sort(
                Comparator
                        .comparing(
                                (AccessibleProject item) ->
                                        item.project().updatedAt()
                        )
                        .reversed()
        );

        return List.copyOf(result);
    }

    @Transactional
    public Project rename(
            UUID userId,
            UUID projectId,
            String name
    ) {
        projectAccessService.requireOwner(userId, projectId);

        ProjectEntity entity = projectRepository
                .findForUpdateById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Project renamed = projectMapper
                .toDomain(entity)
                .rename(name);

        return projectMapper.toDomain(
                projectRepository.save(projectMapper.toEntity(renamed))
        );
    }

    @Transactional(readOnly = true)
    public ProjectDocumentValidationReport validateDocument(
            UUID userId,
            UUID projectId,
            ProjectDocument document
    ) {
        projectAccessService.requireRead(userId, projectId);
        return projectDocumentValidator.analyze(document);
    }

    @Transactional
    public Project saveDocument(
            UUID userId,
            UUID projectId,
            long baseRevision,
            ProjectDocument document
    ) {
        projectAccessService.requireEdit(userId, projectId);
        projectDocumentValidator.validate(document);

        ProjectEntity entity = projectRepository
                .findForUpdateById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Project current = projectMapper.toDomain(entity);

        if (current.revision() != baseRevision) {
            throw new ProjectRevisionConflictException(
                    baseRevision,
                    current.revision()
            );
        }

        Project saved = current.saveDocument(document);

        return projectMapper.toDomain(
                projectRepository.save(projectMapper.toEntity(saved))
        );
    }
}
