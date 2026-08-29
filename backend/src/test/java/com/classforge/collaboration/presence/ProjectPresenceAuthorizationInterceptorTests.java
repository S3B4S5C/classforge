package com.classforge.collaboration.presence;

import com.classforge.collaboration.security.CollaborationPrincipal;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.collaboration.security.ProjectStompAuthorizationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectPresenceAuthorizationInterceptorTests {

    @Test
    void presenceSubscriptionRequiresProjectAccess() {
        ProjectAccessService accessService =
                mock(
                        ProjectAccessService.class
                );

        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(
                        accessService
                );

        UUID userId =
                UUID.randomUUID();

        UUID projectId =
                UUID.randomUUID();

        interceptor.preSend(
                stompMessage(
                        "/topic/projects/"
                                + projectId
                                + "/presence",
                        new CollaborationPrincipal(
                                userId,
                                "owner@classforge.test"
                        )
                ),
                mock(
                        MessageChannel.class
                )
        );

        verify(accessService)
                .requireRead(
                        userId,
                        projectId
                );
    }


    @Test
    void presenceSendRequiresProjectEditAccess() {
        ProjectAccessService accessService = mock(ProjectAccessService.class);
        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(accessService);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        interceptor.preSend(
                stompMessage(
                        StompCommand.SEND,
                        "/app/projects/" + projectId + "/presence",
                        new CollaborationPrincipal(
                                userId,
                                "editor@classforge.test"
                        )
                ),
                mock(MessageChannel.class)
        );

        verify(accessService).requireEdit(userId, projectId);
    }

    private Message<byte[]> stompMessage(
            String destination,
            CollaborationPrincipal principal
    ) {
        return stompMessage(
                StompCommand.SUBSCRIBE,
                destination,
                principal
        );
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String destination,
            CollaborationPrincipal principal
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(command);

        accessor.setDestination(
                destination
        );

        accessor.setUser(
                principal
        );

        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}