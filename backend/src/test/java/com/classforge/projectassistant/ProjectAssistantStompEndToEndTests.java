package com.classforge.projectassistant;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.auth.security.JwtService;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.database=project_assistant_stomp_e2e",
        "classforge.security.jwt.secret=test-secret-long-enough-for-project-assistant-stomp-e2e"
})
class ProjectAssistantStompEndToEndTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ProjectService projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JsonMapper json;
    @Autowired SimpleBrokerMessageHandler broker;
    @MockitoBean ProjectAssistantConversationGateway gateway;

    @Test
    void ownerAndEditorReceiveIdenticalPersistedQuestionAndResponseFromAuthenticatedHttpWithoutChangingUml() throws Exception {
        UserEntity owner = user("Owner"), editor = user("Editor");
        Project project = projects.create(owner.getId(), "Asistente compartido realtime");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
        UUID requestId = UUID.randomUUID();
        when(gateway.respond(any(), eq("¿Qué clases existen?"), eq(AssistantMode.ASK)))
                .thenReturn(new AssistantConversationResponse("El proyecto todavía no contiene clases.", List.of()));
        BlockingQueue<String> ownerEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> editorEvents = new LinkedBlockingQueue<>();
        WebSocketStompClient client = client();
        StompSession ownerSession = null, editorSession = null;
        try {
            ownerSession = connect(client, owner, new StompSessionHandlerAdapter() { });
            editorSession = connect(client, editor, new StompSessionHandlerAdapter() { });
            String destination = topic(project);
            ownerSession.subscribe(destination, collect(ownerEvents));
            editorSession.subscribe(destination, collect(editorEvents));
            // CONNECTED precedes asynchronous subscription registration. Observe broker readiness,
            // rather than guessing a delay or generating a synthetic assistant broadcast.
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20))
                    .until(() -> subscriptionCount(destination) == 2);

            HttpResponse<String> response;
            try (HttpClient http = HttpClient.newHttpClient()) {
                response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                                + "/api/projects/" + project.id() + "/assistant/messages"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + jwt.issue(editor))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                                "content", "¿Qué clases existen?", "mode", "ASK", "requestId", requestId))))
                        .build(), HttpResponse.BodyHandlers.ofString());
            }
            assertEquals(200, response.statusCode(), response.body());
            JsonNode httpResult = json.readTree(response.body());
            JsonNode ownerQuestion = next(ownerEvents), ownerAnswer = next(ownerEvents);
            JsonNode editorQuestion = next(editorEvents), editorAnswer = next(editorEvents);

            assertEvent(ownerQuestion, "ASSISTANT_MESSAGE_CREATED", project, httpResult.get("userMessage"));
            assertEvent(editorQuestion, "ASSISTANT_MESSAGE_CREATED", project, httpResult.get("userMessage"));
            assertEvent(ownerAnswer, "ASSISTANT_RESPONSE_CREATED", project, httpResult.get("assistantMessage"));
            assertEvent(editorAnswer, "ASSISTANT_RESPONSE_CREATED", project, httpResult.get("assistantMessage"));
            assertEquals(ownerQuestion, editorQuestion);
            assertEquals(ownerAnswer, editorAnswer);
            assertEquals(editor.getId().toString(), ownerQuestion.get("message").get("authorUserId").asString());
            assertEquals("USER", ownerQuestion.get("message").get("role").asString());
            assertEquals("ASSISTANT", ownerAnswer.get("message").get("role").asString());
            assertTrue(ownerQuestion.get("message").get("sequence").asLong()
                    < ownerAnswer.get("message").get("sequence").asLong());
            assertNotEquals(ownerQuestion.get("message").get("id"), ownerAnswer.get("message").get("id"));
            Project after = projects.get(owner.getId(), project.id());
            assertEquals(project.revision(), after.revision());
            assertEquals(project.document(), after.document());
            verify(gateway, times(1)).respond(argThat(context -> context.projectId().equals(project.id())
                    && context.currentRevision() == project.revision()), eq("¿Qué clases existen?"), eq(AssistantMode.ASK));
        } finally {
            disconnect(ownerSession);
            disconnect(editorSession);
            client.stop();
        }
    }

    @Test
    void authenticatedOutsiderReceivesStompErrorWhenSubscribingToAnotherProjectsAssistant() throws Exception {
        UserEntity owner = user("Owner"), outsider = user("Outsider");
        Project project = projects.create(owner.getId(), "Conversación privada");
        CompletableFuture<String> errorFrame = new CompletableFuture<>();
        BlockingQueue<String> unauthorizedEvents = new LinkedBlockingQueue<>();
        WebSocketStompClient client = client();
        StompSession session = null;
        try {
            session = connect(client, outsider, new StompSessionHandlerAdapter() {
                @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    // Session handlers receive ERROR frames; MESSAGE frames go to subscription handlers.
                    errorFrame.complete(headers.getFirst("message") + " " + payload);
                }
            });
            session.subscribe(topic(project), collect(unauthorizedEvents));
            assertFalse(errorFrame.get(5, TimeUnit.SECONDS).isBlank(), "Expected a STOMP ERROR rejecting the subscription");
            assertEquals(0, subscriptionCount(topic(project)));
            assertTrue(unauthorizedEvents.isEmpty());
            verifyNoInteractions(gateway);
            assertEquals(project.revision(), projects.get(owner.getId(), project.id()).revision());
        } finally {
            disconnect(session);
            client.stop();
        }
    }

    private UserEntity user(String name) {
        UUID id = UUID.randomUUID();
        return users.save(new UserEntity(id, name, id + "@assistant-stomp.test", "unused", Instant.now()));
    }

    private WebSocketStompClient client() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JsonTextMessageConverter());
        return client;
    }

    private StompSession connect(WebSocketStompClient client, UserEntity user, StompSessionHandlerAdapter handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + jwt.issue(user));
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws", new WebSocketHttpHeaders(), headers, handler)
                .get(5, TimeUnit.SECONDS);
    }

    private int subscriptionCount(String topic) {
        var probe = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, topic).build();
        return broker.getSubscriptionRegistry().findSubscriptions(probe).values().stream().mapToInt(List::size).sum();
    }

    private String topic(Project project) { return "/topic/projects/" + project.id() + "/assistant"; }

    private StompFrameHandler collect(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add((String) payload); }
        };
    }

    private JsonNode next(BlockingQueue<String> queue) throws Exception {
        String payload = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(payload, "Expected an assistant event from the real WebSocket within 5 seconds");
        return json.readTree(payload);
    }

    private void assertEvent(JsonNode event, String type, Project project, JsonNode expectedMessage) {
        assertEquals(type, event.get("type").asString());
        assertEquals(project.id().toString(), event.get("projectId").asString());
        assertEquals(expectedMessage, event.get("message"));
    }

    /** Consume the server's application/json assistant frames and text/plain ERROR frames as raw text. */
    private static class JsonTextMessageConverter extends StringMessageConverter {
        JsonTextMessageConverter() { addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON); }
    }

    private void disconnect(StompSession session) {
        if (session != null && session.isConnected()) {
            try { session.disconnect(); } catch (IllegalStateException alreadyClosed) { /* rejected sessions can close concurrently */ }
        }
    }
}
