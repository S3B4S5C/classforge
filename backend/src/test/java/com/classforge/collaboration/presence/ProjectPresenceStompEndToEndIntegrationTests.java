package com.classforge.collaboration.presence;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.auth.security.JwtService;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-cu07-presence-e2e-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-cu07-presence-e2e"
        }
)
class ProjectPresenceStompEndToEndIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void presenceBroadcastsWithoutChangingProjectRevision()
            throws Exception {
        UUID ownerId =
                UUID.randomUUID();

        UserEntity user =
                userRepository.save(
                        new UserEntity(
                                ownerId,
                                "Presence Owner",
                                "cu07-presence-"
                                        + ownerId
                                        + "@classforge.test",
                                "unused",
                                Instant.now()
                        )
                );

        Project project =
                projectService.create(
                        ownerId,
                        "Presencia"
                );

        String token =
                jwtService.issue(user);

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
                            token
                    );

            sessionB =
                    connect(
                            stompClient,
                            token
                    );

            BlockingQueue<String> topicA =
                    new LinkedBlockingQueue<>();

            BlockingQueue<String> privateB =
                    new LinkedBlockingQueue<>();

            String topic =
                    "/topic/projects/"
                            + project.id()
                            + "/presence";

            sessionA.subscribe(
                    topic,
                    stringHandler(
                            topicA
                    )
            );

            sessionB.subscribe(
                    "/user/queue/projects/"
                            + project.id()
                            + "/presence",
                    stringHandler(
                            privateB
                    )
            );

            Thread.sleep(150);

            UUID clientB =
                    UUID.randomUUID();

            PresenceEventRequest joined =
                    new PresenceEventRequest(
                            UUID.randomUUID(),
                            clientB,
                            PresenceEventType.USER_JOINED,
                            null,
                            null
                    );

            sessionB.send(
                    "/app/projects/"
                            + project.id()
                            + "/presence",
                    jsonMapper.writeValueAsString(
                            joined
                    )
            );

            JsonNode joinedBroadcast =
                    readMessage(
                            topicA
                    );

            assertEquals(
                    "USER_JOINED",
                    joinedBroadcast
                            .get("type")
                            .asString()
            );

            JsonNode snapshot =
                    readMessage(
                            privateB
                    );

            assertEquals(
                    "PRESENCE_SNAPSHOT",
                    snapshot
                            .get("type")
                            .asString()
            );

            PresenceEventRequest cursor =
                    new PresenceEventRequest(
                            UUID.randomUUID(),
                            clientB,
                            PresenceEventType.USER_MOVED_CURSOR,
                            null,
                            new PresenceCursor(
                                    123.5,
                                    456.25
                            )
                    );

            sessionB.send(
                    "/app/projects/"
                            + project.id()
                            + "/presence",
                    jsonMapper.writeValueAsString(
                            cursor
                    )
            );

            JsonNode cursorBroadcast =
                    readMessage(
                            topicA
                    );

            assertEquals(
                    "USER_MOVED_CURSOR",
                    cursorBroadcast
                            .get("type")
                            .asString()
            );

            assertEquals(
                    123.5,
                    cursorBroadcast
                            .get("participant")
                            .get("cursor")
                            .get("x")
                            .asDouble()
            );

            Project reloaded =
                    projectService.get(
                            ownerId,
                            project.id()
                    );

            assertEquals(
                    0L,
                    reloaded.revision()
            );
        } finally {
            if (
                    sessionA != null
                            && sessionA.isConnected()
            ) {
                sessionA.disconnect();
            }

            if (
                    sessionB != null
                            && sessionB.isConnected()
            ) {
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
                "Expected a presence STOMP message within 5 seconds"
        );

        return jsonMapper.readTree(
                message
        );
    }
}