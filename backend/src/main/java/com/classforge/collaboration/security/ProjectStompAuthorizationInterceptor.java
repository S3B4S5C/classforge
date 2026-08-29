package com.classforge.collaboration.security;

import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProjectStompAuthorizationInterceptor
        implements ChannelInterceptor {

    private static final Pattern PROJECT_DESTINATION =
            Pattern.compile(
                    "^/(?:app|topic|user/queue)/projects/"
                            + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                            + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                            + "[0-9a-fA-F]{12})/(?:operations|presence)$"
            );

    private final ProjectAccessService projectAccessService;

    public ProjectStompAuthorizationInterceptor(
            ProjectAccessService projectAccessService
    ) {
        this.projectAccessService = projectAccessService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            throw new MessagingException(
                    "STOMP header accessor is required"
            );
        }

        StompCommand command = accessor.getCommand();

        if (
                command != StompCommand.SEND
                        && command != StompCommand.SUBSCRIBE
        ) {
            return message;
        }

        CollaborationPrincipal principal =
                requirePrincipal(accessor.getUser());

        String destination = accessor.getDestination();

        if (destination == null) {
            throw new MessagingException(
                    "STOMP destination is required"
            );
        }

        enforceDirection(command, destination);

        Matcher matcher = PROJECT_DESTINATION.matcher(destination);

        if (!matcher.matches()) {
            if (isClassForgeProjectDestination(destination)) {
                throw new AccessDeniedException(
                        "Unsupported ClassForge project destination"
                );
            }

            return message;
        }

        UUID projectId = UUID.fromString(matcher.group(1));

        requireProjectPermission(
                command,
                principal.id(),
                projectId
        );

        return message;
    }

    private void requireProjectPermission(
            StompCommand command,
            UUID userId,
            UUID projectId
    ) {
        try {
            if (command == StompCommand.SEND) {
                projectAccessService.requireEdit(userId, projectId);
            } else {
                projectAccessService.requireRead(userId, projectId);
            }
        } catch (ProjectNotFoundException exception) {
            throw new AccessDeniedException(
                    "The authenticated user cannot access this project",
                    exception
            );
        }
    }

    private CollaborationPrincipal requirePrincipal(Principal principal) {
        if (principal instanceof CollaborationPrincipal collaborationPrincipal) {
            return collaborationPrincipal;
        }

        throw new AccessDeniedException(
                "Authenticated STOMP principal is required"
        );
    }

    private void enforceDirection(
            StompCommand command,
            String destination
    ) {
        if (
                command == StompCommand.SEND
                        && (
                        destination.startsWith("/topic/")
                                || destination.startsWith("/queue/")
                                || destination.startsWith("/user/")
                )
        ) {
            throw new AccessDeniedException(
                    "Clients cannot SEND directly to broker destinations"
            );
        }

        if (
                command == StompCommand.SUBSCRIBE
                        && destination.startsWith("/app/")
        ) {
            throw new AccessDeniedException(
                    "Clients cannot SUBSCRIBE to application destinations"
            );
        }
    }

    private boolean isClassForgeProjectDestination(String destination) {
        return destination.startsWith("/app/projects/")
                || destination.startsWith("/topic/projects/")
                || destination.startsWith("/user/queue/projects/");
    }
}
