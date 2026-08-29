package com.classforge.project.web;

import com.classforge.auth.security.CurrentUser;
import com.classforge.project.application.ProjectInvitationConflictException;
import com.classforge.project.application.ProjectInvitationNotFoundException;
import com.classforge.project.application.ProjectInvitationService;
import com.classforge.project.application.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project-invitations")
public class ProjectInvitationInboxController {

    private final ProjectInvitationService projectInvitationService;
    private final CurrentUser currentUser;

    public ProjectInvitationInboxController(
            ProjectInvitationService projectInvitationService,
            CurrentUser currentUser
    ) {
        this.projectInvitationService = projectInvitationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ProjectInvitationResponse> pending() {
        return projectInvitationService
                .pendingForUser(currentUser.id())
                .stream()
                .map(ProjectInvitationResponse::from)
                .toList();
    }

    @PostMapping("/{invitationId}/accept")
    public ProjectInvitationResponse accept(
            @PathVariable UUID invitationId
    ) {
        return ProjectInvitationResponse.from(
                projectInvitationService.accept(
                        currentUser.id(),
                        invitationId
                )
        );
    }

    @PostMapping("/{invitationId}/decline")
    public ProjectInvitationResponse decline(
            @PathVariable UUID invitationId
    ) {
        return ProjectInvitationResponse.from(
                projectInvitationService.decline(
                        currentUser.id(),
                        invitationId
                )
        );
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
