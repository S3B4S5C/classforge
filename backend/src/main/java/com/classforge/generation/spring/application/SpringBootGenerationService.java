package com.classforge.generation.spring.application;

import com.classforge.generation.relational.RelationalMappingException;
import com.classforge.generation.relational.RelationalModelMapper;
import com.classforge.generation.spring.archive.DeterministicZipWriter;
import com.classforge.generation.spring.archive.SpringBootGenerationArtifact;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.planning.SpringGenerationPlanner;
import com.classforge.generation.spring.rendering.SpringProjectRenderer;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.persistence.ProjectMapper;
import com.classforge.project.persistence.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpringBootGenerationService {
    private final ProjectRepository projects;
    private final ProjectMapper mapper;
    private final RelationalModelMapper relationalMapper;
    private final SpringGenerationPlanner planner;
    private final SpringProjectRenderer renderer;
    private final DeterministicZipWriter archive;

    public SpringBootGenerationService(
            ProjectRepository projects,
            ProjectMapper mapper,
            RelationalModelMapper relationalMapper,
            SpringGenerationPlanner planner,
            SpringProjectRenderer renderer,
            DeterministicZipWriter archive
    ) {
        this.projects = projects;
        this.mapper = mapper;
        this.relationalMapper = relationalMapper;
        this.planner = planner;
        this.renderer = renderer;
        this.archive = archive;
    }

    @Transactional(readOnly = true)
    public SpringBootGenerationArtifact generate(
            UUID projectId,
            long baseRevision,
            SpringGenerationConfig config
    ) {
        return generate(projectId, baseRevision, config, SpringBootGenerationOptions.strict());
    }

    @Transactional(readOnly = true)
    public SpringBootGenerationArtifact generate(
            UUID projectId,
            long baseRevision,
            SpringGenerationConfig config,
            SpringBootGenerationOptions options
    ) {
        Project project = projects.findById(projectId)
                .map(mapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        if (project.revision() != baseRevision) {
            throw new ProjectRevisionConflictException(baseRevision, project.revision());
        }

        UmlModel canonicalModel = project.document().umlModel();
        UmlModel generationModel = options.useFirstAttributeAsIdentifier()
                ? SpringBootGenerationUmlPreparation.applyFirstAttributeIdentifiers(canonicalModel)
                : canonicalModel;

        try {
            var springModel = planner.plan(
                    relationalMapper.map(generationModel),
                    config
            );
            byte[] bytes = archive.write(renderer.render(springModel, options));
            return new SpringBootGenerationArtifact(
                    config.artifactName() + "-backend.zip",
                    "application/zip",
                    project.revision(),
                    bytes
            );
        } catch (RelationalMappingException exception) {
            if (!options.useFirstAttributeAsIdentifier()) {
                List<SpringBootGenerationPrimaryKeyFallback> candidates =
                        SpringBootGenerationUmlPreparation.fallbackCandidates(
                                canonicalModel,
                                exception.diagnostics()
                        );
                if (!candidates.isEmpty()) {
                    throw new SpringBootGenerationReadinessException(
                            exception.diagnostics(),
                            candidates
                    );
                }
            }
            throw exception;
        }
    }
}
