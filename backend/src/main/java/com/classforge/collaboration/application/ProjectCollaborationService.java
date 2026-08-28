package com.classforge.collaboration.application;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.collaboration.protocol.CollaborationActor;
import com.classforge.collaboration.protocol.ProjectOperationApplied;
import com.classforge.collaboration.protocol.ProjectOperationRequest;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectEntity;
import com.classforge.project.persistence.ProjectMapper;
import com.classforge.project.persistence.ProjectRepository;
import com.classforge.project.validation.ProjectDocumentValidationException;
import com.classforge.project.validation.ProjectDocumentValidator;
import com.classforge.project.validation.ValidationViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProjectCollaborationService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectDocumentValidator projectDocumentValidator;
    private final ProjectCommandExecutor projectCommandExecutor;
    private final UserRepository userRepository;

    public ProjectCollaborationService(
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            ProjectDocumentValidator projectDocumentValidator,
            ProjectCommandExecutor projectCommandExecutor,
            UserRepository userRepository
    ) {
        this.projectRepository =
                projectRepository;

        this.projectMapper =
                projectMapper;

        this.projectDocumentValidator =
                projectDocumentValidator;

        this.projectCommandExecutor =
                projectCommandExecutor;

        this.userRepository =
                userRepository;
    }

    @Transactional
    public ProjectOperationApplied apply(
            UUID userId,
            UUID destinationProjectId,
            ProjectOperationRequest request
    ) {
        validateEnvelope(
                destinationProjectId,
                request
        );

        ProjectEntity entity =
                projectRepository
                        .findForUpdate(
                                destinationProjectId,
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new ProjectNotFoundException(
                                                destinationProjectId
                                        )
                        );

        Project current =
                projectMapper.toDomain(entity);

        if (
                current.revision()
                        != request.baseRevision()
        ) {
            throw new ProjectOperationRejectedException(
                    "REVISION_CONFLICT",
                    "The project changed before this operation could be applied",
                    current.revision()
            );
        }

        ProjectDocument nextDocument;

        try {
            nextDocument =
                    projectCommandExecutor.execute(
                            current.document(),
                            request.command()
                    );

            projectDocumentValidator.validate(
                    nextDocument
            );
        } catch (
                ProjectCommandRejectedException exception
        ) {
            throw new ProjectOperationRejectedException(
                    exception.getCode(),
                    exception.getMessage(),
                    current.revision()
            );
        } catch (
                ProjectDocumentValidationException exception
        ) {
            ValidationViolation first =
                    exception.getViolations()
                            .stream()
                            .findFirst()
                            .orElse(
                                    new ValidationViolation(
                                            "document",
                                            "VALIDATION_ERROR",
                                            "The command would produce an invalid UML document"
                                    )
                            );

            throw new ProjectOperationRejectedException(
                    first.code(),
                    first.message(),
                    current.revision()
            );
        }

        Project saved =
                current.saveDocument(
                        nextDocument
                );

        Project persisted =
                projectMapper.toDomain(
                        projectRepository.save(
                                projectMapper.toEntity(
                                        saved
                                )
                        )
                );

        UserEntity actor =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user no longer exists"
                                        )
                        );

        return new ProjectOperationApplied(
                request.operationId(),
                persisted.id(),
                request.clientId(),
                persisted.revision(),
                request.command(),
                new CollaborationActor(
                        actor.getId(),
                        actor.getDisplayName()
                ),
                persisted.updatedAt()
        );
    }

    private void validateEnvelope(
            UUID destinationProjectId,
            ProjectOperationRequest request
    ) {
        if (request == null) {
            throw new ProjectOperationRejectedException(
                    "OPERATION_REQUIRED",
                    "The project operation is required",
                    null
            );
        }

        if (request.operationId() == null) {
            throw new ProjectOperationRejectedException(
                    "OPERATION_ID_REQUIRED",
                    "operationId is required",
                    null
            );
        }

        if (request.projectId() == null) {
            throw new ProjectOperationRejectedException(
                    "PROJECT_ID_REQUIRED",
                    "projectId is required",
                    null
            );
        }

        if (
                !destinationProjectId.equals(
                        request.projectId()
                )
        ) {
            throw new ProjectOperationRejectedException(
                    "PROJECT_ID_MISMATCH",
                    "The destination project does not match the operation projectId",
                    null
            );
        }

        if (request.clientId() == null) {
            throw new ProjectOperationRejectedException(
                    "CLIENT_ID_REQUIRED",
                    "clientId is required",
                    null
            );
        }

        if (request.baseRevision() < 0) {
            throw new ProjectOperationRejectedException(
                    "REVISION_INVALID",
                    "baseRevision cannot be negative",
                    null
            );
        }

        if (request.command() == null) {
            throw new ProjectOperationRejectedException(
                    "COMMAND_REQUIRED",
                    "command is required",
                    null
            );
        }
    }
}