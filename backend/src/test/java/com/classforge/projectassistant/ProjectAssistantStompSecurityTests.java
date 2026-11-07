package com.classforge.projectassistant;

import com.classforge.collaboration.security.CollaborationPrincipal;
import com.classforge.collaboration.security.ProjectStompAuthorizationInterceptor;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectAssistantStompSecurityTests {
    Message<byte[]> frame(StompCommand command, String destination, UUID userId) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(new CollaborationPrincipal(userId, "member@test.invalid"));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test void assistantSubscriptionUsesExistingProjectReadPermission() {
        var access = mock(ProjectAccessService.class);
        var interceptor = new ProjectStompAuthorizationInterceptor(access);
        UUID user = UUID.randomUUID(), project = UUID.randomUUID();
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/topic/projects/" + project + "/assistant", user), mock(MessageChannel.class)));
        verify(access).requireRead(user, project);
    }

    @Test void outsiderCannotSubscribeToAssistant() {
        var access = mock(ProjectAccessService.class);
        UUID user = UUID.randomUUID(), project = UUID.randomUUID();
        doThrow(new ProjectNotFoundException(project)).when(access).requireRead(user, project);
        var interceptor = new ProjectStompAuthorizationInterceptor(access);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/topic/projects/" + project + "/assistant", user), mock(MessageChannel.class)));
    }

    @Test void clientsCannotImpersonateAssistantOrPublishToAnUnsupportedApplicationRoute() {
        var access = mock(ProjectAccessService.class);
        var interceptor = new ProjectStompAuthorizationInterceptor(access);
        UUID user = UUID.randomUUID(), project = UUID.randomUUID();
        for (String prefix : new String[]{"/topic", "/app", "/user/queue"}) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND,
                    prefix + "/projects/" + project + "/assistant", user), mock(MessageChannel.class)));
        }
    }
}
