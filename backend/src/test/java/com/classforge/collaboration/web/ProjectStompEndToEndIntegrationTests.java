package com.classforge.collaboration.web;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.auth.security.JwtService;
import com.classforge.collaboration.protocol.ProjectOperationRequest;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-cu06-stomp-e2e-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-cu06-end-to-end-stomp"
        }
)
class ProjectStompEndToEndIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void ownerAndEditorExchangeOperationsAndStaleWriterGetsPrivateRejection()
            throws Exception {
        UUID ownerId = UUID.randomUUID();

        UserEntity owner =
                userRepository.save(
                        new UserEntity(
                                ownerId,
                                "Realtime Owner",
                                "cu31-owner-" + ownerId + "@classforge.test",
                                "unused",
                                Instant.now()
                        )
                );

        UUID editorId = UUID.randomUUID();
        UserEntity editor =
                userRepository.save(
                        new UserEntity(
                                editorId,
                                "Realtime Editor",
                                "cu31-editor-" + editorId + "@classforge.test",
                                "unused",
                                Instant.now()
                        )
                );

        Project project =
                projectService.create(
                        ownerId,
                        "Veterinaria colaborativa"
                );

        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        String ownerToken = jwtService.issue(owner);
        String editorToken = jwtService.issue(editor);

        WebSocketStompClient stompClient =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        stompClient.setMessageConverter(
                new StringMessageConverter()
        );

        StompSession sessionA = null;
        StompSession sessionB = null;

        try {
            sessionA =
                    connect(
                            stompClient,
                            ownerToken
                    );

            sessionB =
                    connect(
                            stompClient,
                            editorToken
                    );

            BlockingQueue<String> topicA =
                    new LinkedBlockingQueue<>();

            BlockingQueue<String> topicB =
                    new LinkedBlockingQueue<>();

            BlockingQueue<String> privateB =
                    new LinkedBlockingQueue<>();

            String topic =
                    "/topic/projects/"
                            + project.id()
                            + "/operations";

            String privateQueue =
                    "/user/queue/projects/"
                            + project.id()
                            + "/operations";

            sessionA.subscribe(
                    topic,
                    stringHandler(topicA)
            );

            sessionB.subscribe(
                    topic,
                    stringHandler(topicB)
            );

            sessionB.subscribe(
                    privateQueue,
                    stringHandler(privateB)
            );

            Thread.sleep(150);

            UUID classId =
                    UUID.randomUUID();

            ProjectOperationRequest accepted =
                    createClassOperation(
                            project.id(),
                            0L,
                            UUID.randomUUID(),
                            classId,
                            "Animal"
                    );

            sessionB.send(
                    "/app/projects/"
                            + project.id()
                            + "/operations",
                    jsonMapper.writeValueAsString(
                            accepted
                    )
            );

            JsonNode receivedA =
                    readMessage(topicA);

            JsonNode receivedB =
                    readMessage(topicB);

            assertEquals(
                    "OPERATION_APPLIED",
                    receivedA.get("type")
                            .asString()
            );

            assertEquals(
                    "OPERATION_APPLIED",
                    receivedB.get("type")
                            .asString()
            );

            assertEquals(
                    1L,
                    receivedA.get("revision")
                            .asLong()
            );

            assertEquals(
                    accepted.operationId()
                            .toString(),
                    receivedB.get("operationId")
                            .asString()
            );

            ProjectOperationRequest stale =
                    createClassOperation(
                            project.id(),
                            0L,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "Cita"
                    );

            sessionB.send(
                    "/app/projects/"
                            + project.id()
                            + "/operations",
                    jsonMapper.writeValueAsString(
                            stale
                    )
            );

            JsonNode rejection =
                    readMessage(privateB);

            assertEquals(
                    "OPERATION_REJECTED",
                    rejection.get("type")
                            .asString()
            );

            assertEquals(
                    "REVISION_CONFLICT",
                    rejection.get("code")
                            .asString()
            );

            assertEquals(
                    1L,
                    rejection.get("currentRevision")
                            .asLong()
            );

            Project persisted =
                    projectService.get(
                            editorId,
                            project.id()
                    );

            assertEquals(
                    1L,
                    persisted.revision()
            );

            assertEquals(
                    classId,
                    persisted.document()
                            .umlModel()
                            .classes()
                            .getFirst()
                            .id()
            );
        } finally {
            if (sessionA != null && sessionA.isConnected()) {
                sessionA.disconnect();
            }

            if (sessionB != null && sessionB.isConnected()) {
                sessionB.disconnect();
            }

            stompClient.stop();
        }
    }

    private StompSession connect(
            WebSocketStompClient stompClient,
            String token
    ) throws Exception {
        StompHeaders connectHeaders =
                new StompHeaders();

        connectHeaders.add(
                "Authorization",
                "Bearer " + token
        );

        return stompClient
                .connectAsync(
                        "ws://127.0.0.1:"
                                + port
                                + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(
                        5,
                        TimeUnit.SECONDS
                );
    }

    private StompFrameHandler stringHandler(
            BlockingQueue<String> queue
    ) {
        return new StompFrameHandler() {

            @Override
            public Type getPayloadType(
                    StompHeaders headers
            ) {
                return String.class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    Object payload
            ) {
                queue.offer(
                        (String) payload
                );
            }
        };
    }

    private JsonNode readMessage(
            BlockingQueue<String> queue
    ) throws Exception {
        String message =
                queue.poll(
                        5,
                        TimeUnit.SECONDS
                );

        assertNotNull(
                message,
                "Expected a STOMP message within 5 seconds"
        );

        return jsonMapper.readTree(
                message
        );
    }

    private ProjectOperationRequest createClassOperation(
            UUID projectId,
            long baseRevision,
            UUID clientId,
            UUID classId,
            String name
    ) {
        return new ProjectOperationRequest(
                UUID.randomUUID(),
                projectId,
                clientId,
                baseRevision,
                new UmlCommandPayload(
                        UUID.randomUUID(),
                        Instant.now(),
                        UmlCommandType.CREATE_CLASS,
                        null,
                        null,
                        new UmlClass(
                                classId,
                                name,
                                List.of()
                        ),
                        new DiagramNodeLayout(
                                80,
                                80,
                                260,
                                160
                        ),
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}