package com.classforge.project.web;

import com.classforge.auth.security.CurrentUser;
import com.classforge.project.application.ProjectInvitationConflictException;
import com.classforge.project.application.ProjectInvitationNotFoundException;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectSharingController {

    private final ProjectInvitationService projectInvitationService;
    private final CurrentUser currentUser;

    public ProjectSharingController(
            ProjectInvitationService projectInvitationService,
            CurrentUser currentUser
    ) {
        this.projectInvitationService = projectInvitationService;
        this.currentUser = currentUser;
    }

    @PostMapping("/invitations")
    public ResponseEntity<ProjectInvitationResponse> invite(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectInvitationRequest request
    ) {
        ProjectInvitationResponse response = ProjectInvitationResponse.from(
                projectInvitationService.invite(
                        currentUser.id(),
                        projectId,
                        request.email()
                )
        );

        return ResponseEntity
                .created(URI.create(
                        "/api/projects/"
                                + projectId
                                + "/invitations/"
                                + response.id()
                ))
                .body(response);
    }

    @GetMapping("/collaborators")
    public ProjectCollaboratorsResponse collaborators(
            @PathVariable UUID projectId
    ) {
        return ProjectCollaboratorsResponse.from(
                projectInvitationService.collaborators(
                        currentUser.id(),
                        projectId
                )
        );
    }

    @GetMapping("/invitations")
    public List<ProjectInvitationResponse> pendingProjectInvitations(
            @PathVariable UUID projectId
    ) {
        return projectInvitationService
                .pendingForProject(currentUser.id(), projectId)
                .stream()
                .map(ProjectInvitationResponse::from)
                .toList();
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId
    ) {
        projectInvitationService.cancel(
                currentUser.id(),
                projectId,
                invitationId
        );
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProjectInvitationErrorResponse handleProjectNotFound(
            ProjectNotFoundException exception
    ) {
        return new ProjectInvitationErrorResponse(
                "PROJECT_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ProjectInvitationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProjectInvitationErrorResponse handleInvitationNotFound(
            ProjectInvitationNotFoundException exception
    ) {
        return new ProjectInvitationErrorResponse(
                "PROJECT_INVITATION_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ProjectInvitationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProjectInvitationErrorResponse handleInvitationConflict(
            ProjectInvitationConflictException exception
    ) {
        return new ProjectInvitationErrorResponse(
                exception.getCode(),
                exception.getMessage()
        );
    }
}
