package com.classforge.collaboration.presence;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.collaboration.security.CollaborationPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.UUID;

@Controller
public class ProjectPresenceController {

    private final ProjectPresenceRegistry registry;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    public ProjectPresenceController(
            ProjectPresenceRegistry registry,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            JsonMapper jsonMapper
    ) {
        this.registry =
                registry;

        this.userRepository =
                userRepository;

        this.messagingTemplate =
                messagingTemplate;

        this.jsonMapper =
                jsonMapper;
    }

    @MessageMapping("/projects/{projectId}/presence")
    public void handlePresence(
            @DestinationVariable UUID projectId,
            String payload,
            Principal principal,
            @Header("simpSessionId") String sessionId
    ) {
        CollaborationPrincipal user =
                requirePrincipal(
                        principal
                );

        PresenceEventRequest request;

        try {
            request =
                    jsonMapper.readValue(
                            payload,
                            PresenceEventRequest.class
                    );
        } catch (JacksonException exception) {
            return;
        }

        if (
                request == null
                        || request.eventId() == null
                        || request.clientId() == null
                        || request.type() == null
        ) {
            return;
        }

        try {
            switch (request.type()) {
                case USER_JOINED ->
                        join(
                                projectId,
                                sessionId,
                                user,
                                request
                        );

                case USER_LEFT ->
                        leave(
                                projectId,
                                sessionId,
                                request.clientId()
                        );

                case USER_SELECTED_ELEMENT ->
                        broadcast(
                                PresenceEventType.USER_SELECTED_ELEMENT,
                                projectId,
                                registry.select(
                                        projectId,
                                        sessionId,
                                        request.clientId(),
                                        request.selectedElement()
                                )
                        );

                case USER_MOVED_CURSOR ->
                        broadcast(
                                PresenceEventType.USER_MOVED_CURSOR,
                                projectId,
                                registry.moveCursor(
                                        projectId,
                                        sessionId,
                                        request.clientId(),
                                        request.cursor()
                                )
                        );
            }
        } catch (PresenceRejectedException exception) {
            /*
             * Presencia es efimera. Un frame invalido no modifica
             * modelo, revision ni persistencia y simplemente se ignora.
             */
        }
    }

    @EventListener
    public void onDisconnect(
            SessionDisconnectEvent event
    ) {
        ProjectPresenceRegistry.RemovedPresence removed =
                registry.disconnect(
                        event.getSessionId()
                );

        if (removed == null) {
            return;
        }

        broadcast(
                PresenceEventType.USER_LEFT,
                removed.projectId(),
                removed.participant()
        );
    }

    private void join(
            UUID projectId,
            String sessionId,
            CollaborationPrincipal principal,
            PresenceEventRequest request
    ) {
        UserEntity user =
                userRepository
                        .findById(
                                principal.id()
                        )
                        .orElse(null);

        if (user == null) {
            return;
        }

        PresenceParticipant participant =
                registry.join(
                        projectId,
                        sessionId,
                        request.clientId(),
                        new PresenceActor(
                                user.getId(),
                                user.getDisplayName()
                        )
                );

        messagingTemplate
                .convertAndSendToUser(
                        principal.getName(),
                        privateQueue(
                                projectId
                        ),
                        serialize(
                                new ProjectPresenceSnapshot(
                                        projectId,
                                        registry.snapshot(
                                                projectId
                                        )
                                )
                        )
                );

        broadcast(
                PresenceEventType.USER_JOINED,
                projectId,
                participant
        );
    }

    private void leave(
            UUID projectId,
            String sessionId,
            UUID clientId
    ) {
        ProjectPresenceRegistry.RemovedPresence removed =
                registry.leave(
                        projectId,
                        sessionId,
                        clientId
                );

        if (removed == null) {
            return;
        }

        broadcast(
                PresenceEventType.USER_LEFT,
                removed.projectId(),
                removed.participant()
        );
    }

    private void broadcast(
            PresenceEventType type,
            UUID projectId,
            PresenceParticipant participant
    ) {
        messagingTemplate.convertAndSend(
                topic(projectId),
                serialize(
                        new ProjectPresenceEvent(
                                type,
                                projectId,
                                participant
                        )
                )
        );
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

    private String serialize(
            Object value
    ) {
        try {
            return jsonMapper.writeValueAsString(
                    value
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize presence message",
                    exception
            );
        }
    }

    private String topic(
            UUID projectId
    ) {
        return "/topic/projects/"
                + projectId
                + "/presence";
    }

    private String privateQueue(
            UUID projectId
    ) {
        return "/queue/projects/"
                + projectId
                + "/presence";
    }
}