package com.classforge.project.web;

import com.classforge.auth.security.CurrentUser;
import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(
            ProjectService projectService,
            CurrentUser currentUser
    ) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse project = ProjectResponse.from(
                projectService.create(currentUser.id(), request.name()),
                ProjectAccessRole.OWNER
        );

        return ResponseEntity
                .created(URI.create("/api/projects/" + project.id()))
                .body(project);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService
                .listAccessible(currentUser.id())
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(
                projectService.getAccessible(
                        currentUser.id(),
                        projectId
                )
        );
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse rename(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ProjectResponse.from(
                projectService.rename(
                        currentUser.id(),
                        projectId,
                        request.name()
                ),
                ProjectAccessRole.OWNER
        );
    }

    @PostMapping("/{projectId}/validate")
    public ProjectValidationResponse validateDocument(
            @PathVariable UUID projectId,
            @Valid @RequestBody ValidateProjectDocumentRequest request
    ) {
        return ProjectValidationResponse.from(
                projectService.validateDocument(
                        currentUser.id(),
                        projectId,
                        request.document()
                )
        );
    }

    @PutMapping("/{projectId}/document")
    public ProjectResponse saveDocument(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveProjectDocumentRequest request
    ) {
        ProjectAccessRole accessRole =
                projectService.accessRole(
                        currentUser.id(),
                        projectId
                );

        return ProjectResponse.from(
                projectService.saveDocument(
                        currentUser.id(),
                        projectId,
                        request.baseRevision(),
                        request.document()
                ),
                accessRole
        );
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(
            ProjectNotFoundException exception
    ) {
        return Map.of(
                "error", "PROJECT_NOT_FOUND",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(ProjectRevisionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProjectRevisionConflictResponse handleRevisionConflict(
            ProjectRevisionConflictException exception
    ) {
        return new ProjectRevisionConflictResponse(
                "PROJECT_REVISION_CONFLICT",
                exception.getMessage(),
                exception.getRequestedRevision(),
                exception.getCurrentRevision()
        );
    }
}
