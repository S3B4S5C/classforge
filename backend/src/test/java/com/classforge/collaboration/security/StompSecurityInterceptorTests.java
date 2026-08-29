package com.classforge.collaboration.security;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.security.JwtService;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompSecurityInterceptorTests {

    @Test
    void connectRequiresAndInstallsJwtPrincipal() {
        JwtService jwtService =
                new JwtService(
                        "test-secret-long-enough-for-cu06-stomp-jwt",
                        60
                );

        UUID userId =
                UUID.randomUUID();

        UserEntity user =
                new UserEntity(
                        userId,
                        "STOMP User",
                        "stomp@classforge.test",
                        "unused",
                        Instant.now()
                );

        String token =
                jwtService.issue(user);

        StompJwtAuthenticationInterceptor interceptor =
                new StompJwtAuthenticationInterceptor(
                        jwtService
                );

        Message<byte[]> message =
                stompMessage(
                        StompCommand.CONNECT,
                        null,
                        token,
                        null
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        mock(
                                org.springframework.messaging.MessageChannel.class
                        )
                );

        StompHeaderAccessor accessor =
                org.springframework.messaging.support.MessageHeaderAccessor
                        .getAccessor(
                                result,
                                StompHeaderAccessor.class
                        );

        assertInstanceOf(
                StompHeaderAccessor.class,
                accessor
        );

        CollaborationPrincipal principal =
                assertInstanceOf(
                        CollaborationPrincipal.class,
                        accessor.getUser()
                );

        assertEquals(
                userId,
                principal.id()
        );
    }

    @Test
    void connectWithoutTokenIsRejected() {
        StompJwtAuthenticationInterceptor interceptor =
                new StompJwtAuthenticationInterceptor(
                        new JwtService(
                                "test-secret-long-enough-for-cu06-stomp-jwt",
                                60
                        )
                );

        assertThrows(
                MessagingException.class,
                () ->
                        interceptor.preSend(
                                stompMessage(
                                        StompCommand.CONNECT,
                                        null,
                                        null,
                                        null
                                ),
                                mock(
                                        org.springframework.messaging.MessageChannel.class
                                )
                        )
        );
    }

    @Test
    void subscribeRequiresProjectAccess() {
        ProjectAccessService accessService =
                mock(
                        ProjectAccessService.class
                );

        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(
                        accessService
                );

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        interceptor.preSend(
                stompMessage(
                        StompCommand.SUBSCRIBE,
                        "/topic/projects/"
                                + projectId
                                + "/operations",
                        null,
                        new CollaborationPrincipal(
                                userId,
                                "owner@classforge.test"
                        )
                ),
                mock(
                        org.springframework.messaging.MessageChannel.class
                )
        );

        verify(accessService)
                .requireRead(
                        userId,
                        projectId
                );
    }

    @Test
    void sendRequiresProjectEditAccess() {
        ProjectAccessService accessService = mock(ProjectAccessService.class);
        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(accessService);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        interceptor.preSend(
                stompMessage(
                        StompCommand.SEND,
                        "/app/projects/" + projectId + "/operations",
                        null,
                        new CollaborationPrincipal(
                                userId,
                                "editor@classforge.test"
                        )
                ),
                mock(org.springframework.messaging.MessageChannel.class)
        );

        verify(accessService).requireEdit(userId, projectId);
    }

    @Test
    void clientCannotBypassApplicationControllerBySendingToTopic() {
        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(
                        mock(
                                ProjectAccessService.class
                        )
                );

        assertThrows(
                AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                stompMessage(
                                        StompCommand.SEND,
                                        "/topic/projects/"
                                                + UUID.randomUUID()
                                                + "/operations",
                                        null,
                                        new CollaborationPrincipal(
                                                UUID.randomUUID(),
                                                "owner@classforge.test"
                                        )
                                ),
                                mock(
                                        org.springframework.messaging.MessageChannel.class
                                )
                        )
        );
    }

    @Test
    void deniedProjectAccessRejectsSubscription() {
        ProjectAccessService accessService =
                mock(
                        ProjectAccessService.class
                );

        UUID projectId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(
                new ProjectNotFoundException(projectId)
        ).when(accessService).requireRead(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(projectId)
        );

        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(
                        accessService
                );

        assertThrows(
                AccessDeniedException.class,
                () ->
                        interceptor.preSend(
                                stompMessage(
                                        StompCommand.SUBSCRIBE,
                                        "/topic/projects/"
                                                + projectId
                                                + "/operations",
                                        null,
                                        new CollaborationPrincipal(
                                                UUID.randomUUID(),
                                                "other@classforge.test"
                                        )
                                ),
                                mock(
                                        org.springframework.messaging.MessageChannel.class
                                )
                        )
        );
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String destination,
            String token,
            CollaborationPrincipal principal
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        command
                );

        if (destination != null) {
            accessor.setDestination(
                    destination
            );
        }

        if (token != null) {
            accessor.setNativeHeader(
                    "Authorization",
                    "Bearer " + token
            );
        }

        if (principal != null) {
            accessor.setUser(
                    principal
            );
        }

        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}