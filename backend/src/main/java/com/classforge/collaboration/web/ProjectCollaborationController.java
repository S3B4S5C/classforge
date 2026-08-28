package com.classforge.collaboration.web;

import com.classforge.collaboration.application.ProjectCollaborationService;
import com.classforge.collaboration.application.ProjectOperationRejectedException;
import com.classforge.collaboration.protocol.ProjectOperationApplied;
import com.classforge.collaboration.protocol.ProjectOperationRejected;
import com.classforge.collaboration.protocol.ProjectOperationRequest;
import com.classforge.collaboration.security.CollaborationPrincipal;
import com.classforge.project.application.ProjectNotFoundException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.UUID;

@Controller
public class ProjectCollaborationController {

    private final ProjectCollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    public ProjectCollaborationController(
            ProjectCollaborationService collaborationService,
            SimpMessagingTemplate messagingTemplate,
            JsonMapper jsonMapper
    ) {
        this.collaborationService =
                collaborationService;

        this.messagingTemplate =
                messagingTemplate;

        this.jsonMapper =
                jsonMapper;
    }

    @MessageMapping("/projects/{projectId}/operations")
    public void apply(
            @DestinationVariable UUID projectId,
            String payload,
            Principal principal
    ) {
        CollaborationPrincipal user =
                requirePrincipal(principal);

        ProjectOperationRequest request;

        try {
            request =
                    jsonMapper.readValue(
                            payload,
                            ProjectOperationRequest.class
                    );
        } catch (JacksonException exception) {
            reject(
                    user,
                    projectId,
                    null,
                    null,
                    "INVALID_OPERATION_JSON",
                    "The STOMP payload is not a valid ProjectOperation",
                    null
            );
            return;
        }

        try {
            ProjectOperationApplied applied =
                    collaborationService.apply(
                            user.id(),
                            projectId,
                            request
                    );

            messagingTemplate.convertAndSend(
                    topic(projectId),
                    serialize(applied)
            );
        } catch (
                ProjectOperationRejectedException exception
        ) {
            reject(
                    user,
                    projectId,
                    request.operationId(),
                    request.clientId(),
                    exception.getCode(),
                    exception.getMessage(),
                    exception.getCurrentRevision()
            );
        } catch (
                ProjectNotFoundException exception
        ) {
            reject(
                    user,
                    projectId,
                    request.operationId(),
                    request.clientId(),
                    "PROJECT_NOT_FOUND",
                    "The project is unavailable to the authenticated user",
                    null
            );
        }
    }

    private CollaborationPrincipal requirePrincipal(
            Principal principal
    ) {
        if (
                principal
                        instanceof CollaborationPrincipal collaborationPrincipal
        ) {
            return collaborationPrincipal;
        }

        throw new IllegalStateException(
                "Authenticated collaboration principal is required"
        );
    }

    private void reject(
            CollaborationPrincipal user,
            UUID projectId,
            UUID operationId,
            UUID clientId,
            String code,
            String message,
            Long currentRevision
    ) {
        ProjectOperationRejected rejection =
                new ProjectOperationRejected(
                        operationId,
                        projectId,
                        clientId,
                        code,
                        message,
                        currentRevision
                );

        messagingTemplate.convertAndSendToUser(
                user.getName(),
                privateQueue(projectId),
                serialize(rejection)
        );
    }

    private String serialize(
            Object value
    ) {
        try {
            return jsonMapper.writeValueAsString(
                    value
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize collaboration message",
                    exception
            );
        }
    }

    private String topic(
            UUID projectId
    ) {
        return "/topic/projects/"
                + projectId
                + "/operations";
    }

    private String privateQueue(
            UUID projectId
    ) {
        return "/queue/projects/"
                + projectId
                + "/operations";
    }
}