package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectEntity;
import com.classforge.project.persistence.ProjectMapper;
import com.classforge.project.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMapper projectMapper
    ) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public Project create(UUID ownerId, String name) {
        Project project = Project.create(ownerId, name);

        return projectMapper.toDomain(
                projectRepository.save(projectMapper.toEntity(project))
        );
    }

    @Transactional(readOnly = true)
    public Project get(UUID ownerId, UUID projectId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .map(projectMapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    @Transactional(readOnly = true)
    public List<Project> list(UUID ownerId) {
        return projectRepository
                .findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                .stream()
                .map(projectMapper::toDomain)
                .toList();
    }

    @Transactional
    public Project rename(
            UUID ownerId,
            UUID projectId,
            String name
    ) {
        ProjectEntity entity = projectRepository
                .findForUpdate(projectId, ownerId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Project renamed = projectMapper
                .toDomain(entity)
                .rename(name);

        return projectMapper.toDomain(
                projectRepository.save(projectMapper.toEntity(renamed))
        );
    }

    @Transactional
    public Project saveDocument(
            UUID ownerId,
            UUID projectId,
            long baseRevision,
            ProjectDocument document
    ) {
        ProjectEntity entity = projectRepository
                .findForUpdate(projectId, ownerId)
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