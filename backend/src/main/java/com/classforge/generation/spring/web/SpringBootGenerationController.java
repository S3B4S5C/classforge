package com.classforge.generation.spring.web;

import com.classforge.auth.security.CurrentUser;
import com.classforge.generation.relational.RelationalMappingDiagnostic;
import com.classforge.generation.relational.RelationalMappingException;
import com.classforge.generation.spring.api.SpringApiGenerationDiagnostic;
import com.classforge.generation.spring.api.SpringApiGenerationException;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.application.SpringBootGenerationReadinessException;
import com.classforge.generation.spring.application.SpringBootGenerationService;
import com.classforge.generation.spring.archive.SpringArchiveException;
import com.classforge.generation.spring.archive.SpringBootGenerationArtifact;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnosticCode;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.validation.SpringGenerationDiagnostic;
import com.classforge.generation.spring.validation.SpringGenerationDiagnosticCode;
import com.classforge.generation.spring.validation.SpringGenerationException;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.web.ProjectRevisionConflictResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/generation")
public class SpringBootGenerationController {
    private static final Set<SpringGenerationDiagnosticCode> CONFIGURATION_CODES = Set.of(
            SpringGenerationDiagnosticCode.INVALID_ARTIFACT_NAME,
            SpringGenerationDiagnosticCode.INVALID_BASE_PACKAGE
    );
    private static final Set<GeneratedProjectDiagnosticCode> TEMPLATE_CODES = Set.of(
            GeneratedProjectDiagnosticCode.TEMPLATE_NOT_FOUND,
            GeneratedProjectDiagnosticCode.TEMPLATE_RENDER_FAILED,
            GeneratedProjectDiagnosticCode.STATIC_RESOURCE_NOT_FOUND
    );

    private final SpringBootGenerationService generation;
    private final ProjectAccessService access;
    private final CurrentUser currentUser;

    public SpringBootGenerationController(
            SpringBootGenerationService generation,
            ProjectAccessService access,
            CurrentUser currentUser
    ) {
        this.generation = generation;
        this.access = access;
        this.currentUser = currentUser;
    }

    @PostMapping("/spring-boot")
    public ResponseEntity<byte[]> generate(
            @PathVariable UUID projectId,
            @Valid @RequestBody SpringBootGenerationRequest request
    ) {
        access.requireEdit(currentUser.id(), projectId);
        SpringBootGenerationArtifact artifact = generation.generate(
                projectId,
                request.baseRevision(),
                new SpringGenerationConfig(request.artifactName(), request.basePackage()),
                new SpringBootGenerationOptions(
                        request.firstAttributeIdentifierFallbackEnabled(),
                        request.effectiveMode(),
                        request.authClassId(),
                        request.usernameAttributeId(),
                        request.passwordAttributeId()
                )
        );
        byte[] content = artifact.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(artifact.fileName()).build().toString()
                )
                .body(content);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(ProjectNotFoundException exception) {
        return Map.of(
                "error", "PROJECT_NOT_FOUND",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(ProjectRevisionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProjectRevisionConflictResponse revisionConflict(ProjectRevisionConflictException exception) {
        return new ProjectRevisionConflictResponse(
                "STALE_PROJECT_REVISION",
                exception.getMessage(),
                exception.getRequestedRevision(),
                exception.getCurrentRevision()
        );
    }

    @ExceptionHandler(SpringBootGenerationReadinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SpringBootGenerationErrorResponse primaryKeyFallbackAvailable(
            SpringBootGenerationReadinessException exception
    ) {
        return new SpringBootGenerationErrorResponse(
                "PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED",
                "Hay clases sin clave primaria explicita que pueden exportarse usando su primer atributo.",
                exception.diagnostics().stream().map(this::toDiagnosticResponse).toList(),
                exception.primaryKeyFallbacks().stream()
                        .map(fallback -> new SpringBootGenerationPrimaryKeyFallbackResponse(
                                fallback.className(),
                                fallback.attributeName()
                        ))
                        .toList()
        );
    }

    @ExceptionHandler(RelationalMappingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SpringBootGenerationErrorResponse relationalMappingRejected(RelationalMappingException exception) {
        List<SpringBootGenerationDiagnosticResponse> diagnostics = exception.diagnostics()
                .stream()
                .map(this::toDiagnosticResponse)
                .toList();
        return new SpringBootGenerationErrorResponse(
                "RELATIONAL_MAPPING_REJECTED",
                "El modelo UML no puede transformarse a un modelo relacional valido.",
                diagnostics,
                List.of()
        );
    }

    @ExceptionHandler(SpringApiGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SpringBootGenerationErrorResponse apiGenerationRejected(SpringApiGenerationException exception) {
        return new SpringBootGenerationErrorResponse(
                "INVALID_API_GENERATION_CONFIGURATION",
                "La configuracion CRUD/Auth no es valida.",
                exception.diagnostics().stream().map(this::toDiagnosticResponse).toList(),
                List.of()
        );
    }

    @ExceptionHandler(SpringGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SpringBootGenerationErrorResponse springGenerationRejected(SpringGenerationException exception) {
        boolean configurationFailure = !exception.diagnostics().isEmpty()
                && exception.diagnostics().stream()
                .allMatch(diagnostic -> CONFIGURATION_CODES.contains(diagnostic.code()));

        String error = configurationFailure
                ? "INVALID_GENERATION_CONFIGURATION"
                : "SPRING_MODEL_REJECTED";
        String message = configurationFailure
                ? "La configuracion de generacion Spring Boot no es valida."
                : "El modelo no puede convertirse a un proyecto Spring Boot valido.";

        return new SpringBootGenerationErrorResponse(
                error,
                message,
                exception.diagnostics().stream().map(this::toDiagnosticResponse).toList(),
                List.of()
        );
    }

    @ExceptionHandler(GeneratedProjectException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public SpringBootGenerationErrorResponse generatedProjectFailure(GeneratedProjectException exception) {
        boolean templateFailure = exception.diagnostics().stream()
                .anyMatch(diagnostic -> TEMPLATE_CODES.contains(diagnostic.code()));
        return internalFailure(
                templateFailure ? "TEMPLATE_RENDER_FAILED" : "GENERATED_PROJECT_INVALID"
        );
    }

    @ExceptionHandler(SpringArchiveException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public SpringBootGenerationErrorResponse archiveFailure(SpringArchiveException exception) {
        return internalFailure("ARCHIVE_FAILED");
    }

    private SpringBootGenerationDiagnosticResponse toDiagnosticResponse(RelationalMappingDiagnostic diagnostic) {
        return new SpringBootGenerationDiagnosticResponse(
                diagnostic.code().name(),
                diagnostic.elementId(),
                diagnostic.path(),
                diagnostic.message()
        );
    }

    private SpringBootGenerationDiagnosticResponse toDiagnosticResponse(SpringGenerationDiagnostic diagnostic) {
        return new SpringBootGenerationDiagnosticResponse(
                diagnostic.code().name(),
                diagnostic.sourceElementId(),
                diagnostic.path(),
                diagnostic.message()
        );
    }

    private SpringBootGenerationDiagnosticResponse toDiagnosticResponse(SpringApiGenerationDiagnostic diagnostic) {
        return new SpringBootGenerationDiagnosticResponse(
                diagnostic.code().name(),
                diagnostic.elementId(),
                diagnostic.path(),
                diagnostic.message()
        );
    }

    private SpringBootGenerationErrorResponse internalFailure(String error) {
        return new SpringBootGenerationErrorResponse(
                error,
                "No se pudo generar el proyecto Spring Boot.",
                List.of(),
                List.of()
        );
    }
}
