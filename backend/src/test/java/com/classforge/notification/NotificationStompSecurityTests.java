package com.classforge.notification;

import com.classforge.collaboration.security.*;
import com.classforge.project.access.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationStompSecurityTests {
    final ProjectAccessService access = mock(ProjectAccessService.class);
    final ProjectStompAuthorizationInterceptor interceptor = new ProjectStompAuthorizationInterceptor(access);
    Message<byte[]> message(StompCommand command, String destination, boolean authenticated) {
        var headers = StompHeaderAccessor.create(command); headers.setDestination(destination); headers.setSessionId("test-session");
        if (authenticated) headers.setUser(new CollaborationPrincipal(UUID.randomUUID(), "test@test.invalid"));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
    @Test void authenticatedUserCanSubscribeOnlyToTheStandardPrivateNotificationDestination() {
        var message = message(StompCommand.SUBSCRIBE, "/user/queue/notifications", true);
        assertSame(message, interceptor.preSend(message, mock(MessageChannel.class))); verifyNoInteractions(access);
    }
    @Test void guessedUserIdsPublicTopicsAndClientPublishingAreRejected() {
        for (String destination : List.of("/user/" + UUID.randomUUID() + "/queue/notifications", "/topic/notifications", "/queue/notifications")) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, destination, true), mock(MessageChannel.class)));
        }
        for (String destination : List.of("/user/queue/notifications", "/app/notifications", "/topic/notifications")) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SEND, destination, true), mock(MessageChannel.class)));
        }
    }
    @Test void privateNotificationsRequireAnAuthenticatedPrincipal() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/user/queue/notifications", false), mock(MessageChannel.class)));
    }
}
