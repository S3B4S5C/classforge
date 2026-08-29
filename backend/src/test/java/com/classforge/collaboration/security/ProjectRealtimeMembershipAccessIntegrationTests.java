package com.classforge.collaboration.security;

import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu31-realtime-access-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectRealtimeMembershipAccessIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private ProjectAccessService projectAccessService;

    @Test
    void ownerAndEditorCanUseRealtimeDestinationsButNoneCannot() {
        UUID ownerId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();

        Project project = projectService.create(ownerId, "Realtime CU31");
        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        ProjectStompAuthorizationInterceptor interceptor =
                new ProjectStompAuthorizationInterceptor(projectAccessService);

        assertRealtimeAccess(interceptor, ownerId, project.id());
        assertRealtimeAccess(interceptor, editorId, project.id());

        CollaborationPrincipal none =
                new CollaborationPrincipal(
                        noneId,
                        "none@classforge.test"
                );

        assertDenied(interceptor, StompCommand.SUBSCRIBE,
                "/topic/projects/" + project.id() + "/operations", none);
        assertDenied(interceptor, StompCommand.SUBSCRIBE,
                "/topic/projects/" + project.id() + "/presence", none);
        assertDenied(interceptor, StompCommand.SEND,
                "/app/projects/" + project.id() + "/operations", none);
        assertDenied(interceptor, StompCommand.SEND,
                "/app/projects/" + project.id() + "/presence", none);
    }

    private void assertRealtimeAccess(
            ProjectStompAuthorizationInterceptor interceptor,
            UUID userId,
            UUID projectId
    ) {
        CollaborationPrincipal principal =
                new CollaborationPrincipal(
                        userId,
                        userId + "@classforge.test"
                );

        assertDoesNotThrow(() -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE,
                        "/topic/projects/" + projectId + "/operations", principal),
                mock(MessageChannel.class)
        ));

        assertDoesNotThrow(() -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE,
                        "/user/queue/projects/" + projectId + "/presence", principal),
                mock(MessageChannel.class)
        ));

        assertDoesNotThrow(() -> interceptor.preSend(
                message(StompCommand.SEND,
                        "/app/projects/" + projectId + "/operations", principal),
                mock(MessageChannel.class)
        ));

        assertDoesNotThrow(() -> interceptor.preSend(
                message(StompCommand.SEND,
                        "/app/projects/" + projectId + "/presence", principal),
                mock(MessageChannel.class)
        ));
    }

    private void assertDenied(
            ProjectStompAuthorizationInterceptor interceptor,
            StompCommand command,
            String destination,
            CollaborationPrincipal principal
    ) {
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        message(command, destination, principal),
                        mock(MessageChannel.class)
                )
        );
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            CollaborationPrincipal principal
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}
