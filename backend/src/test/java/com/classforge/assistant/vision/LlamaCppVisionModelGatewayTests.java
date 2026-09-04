package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaCppVisionModelGatewayTests {

    @Test
    void sendsMultimodalSchemaRequestWithoutProjectUuidsAndParsesProposal() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        AtomicReference<String> captured = new AtomicReference<>();
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    json(
                            exchange,
                            200,
                            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"summary\\\":\\\"Clase visible\\\",\\\"classes\\\":[{\\\"ref\\\":\\\"c1\\\",\\\"name\\\":\\\"Mascota\\\",\\\"attributes\\\":[],\\\"evidence\\\":{\\\"label\\\":\\\"Mascota\\\",\\\"confidence\\\":0.95}}],\\\"relationships\\\":[],\\\"warnings\\\":[],\\\"confidence\\\":0.95}\"}}]}"
                    );
                }
        );
        server.start();

        try {
            LlamaCppVisionModelGateway gateway = gateway(mapper, baseUrl(server));
            UUID projectId = UUID.randomUUID();
            UUID classId = UUID.randomUUID();
            VisionProjectContext context = new VisionProjectContext(
                    projectId,
                    7L,
                    List.of(new VisionExistingClassContext(classId, "Mascota", List.of("nombre")))
            );

            VisionUmlProposal proposal = gateway.analyze(image(), context);

            assertEquals("Mascota", proposal.safeClasses().getFirst().name());
            String request = captured.get();
            assertFalse(request.contains(projectId.toString()));
            assertFalse(request.contains(classId.toString()));

            JsonNode root = mapper.readTree(request);
            assertEquals("vision-model", root.get("model").asString());
            assertEquals("json_object", root.get("response_format").get("type").asString());
            assertFalse(root.get("response_format").get("schema").get("additionalProperties").asBoolean());
            assertEquals("none", root.get("reasoning_effort").asString());

            JsonNode content = root.get("messages").get(1).get("content");
            assertEquals("text", content.get(0).get("type").asString());
            assertEquals("image_url", content.get(1).get("type").asString());
            assertTrue(
                    content.get(1).get("image_url").get("url").asString()
                            .startsWith("data:image/png;base64,")
            );
        } finally {
            server.stop(0);
        }
    }


    @Test
    void denseTwoPassUsesClosedClassRefsAndReplacesFirstPassRelationships() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> secondRequest = new AtomicReference<>();
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        json(exchange, 200, completion("""
                                {"summary":"dense","classes":[
                                  {"ref":"c1","name":"A","attributes":[],"evidence":{"label":"A"}},
                                  {"ref":"c2","name":"B","attributes":[],"evidence":{"label":"B"}},
                                  {"ref":"c3","name":"C","attributes":[],"evidence":{"label":"C"}},
                                  {"ref":"c4","name":"D","attributes":[],"evidence":{"label":"D"}}
                                ],"relationships":[
                                  {"sourceRef":"c1","targetRef":"c3","type":"ASSOCIATION","evidence":{"label":"A-C"}}
                                ],"warnings":[],"confidence":0.9}
                                """));
                    } else {
                        secondRequest.set(request);
                        json(exchange, 200, completion("""
                                {"relationships":[
                                  {"sourceRef":"c1","targetRef":"c2","type":"ASSOCIATION","evidence":{"label":"A-B"}},
                                  {"sourceRef":"c3","targetRef":"c4","type":"ASSOCIATION","evidence":{"label":"C-D"}}
                                ],"warnings":[],"confidence":0.8}
                                """));
                    }
                }
        );
        server.start();

        try {
            LlamaCppVisionModelGateway gateway = twoPassGateway(mapper, baseUrl(server));
            VisionUmlProposal proposal = gateway.analyze(image(), emptyContext());

            assertEquals(2, calls.get());
            assertEquals(2, proposal.safeRelationships().size());
            assertEquals("c2", proposal.safeRelationships().getFirst().targetRef());
            assertEquals(0.8, proposal.confidence(), 0.0001);

            JsonNode request = mapper.readTree(secondRequest.get());
            assertEquals(
                    "VisionRelationshipPassProposal",
                    request.get("response_format").get("schema").get("title").asString()
            );
            assertEquals(600, request.get("max_tokens").asInt());
            String userPrompt = request.get("messages").get(1).get("content").get(0).get("text").asString();
            assertTrue(userPrompt.contains("c1 = \"A\""));
            assertTrue(userPrompt.contains("c4 = \"D\""));
            assertTrue(request.get("messages").get(0).get("content").asString().contains("lista CERRADA"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void denseTwoPassRejectsRelationshipRefsOutsideFirstPass() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    if (calls.incrementAndGet() == 1) {
                        json(exchange, 200, completion("""
                                {"summary":"dense","classes":[
                                  {"ref":"c1","name":"A","attributes":[],"evidence":{"label":"A"}},
                                  {"ref":"c2","name":"B","attributes":[],"evidence":{"label":"B"}},
                                  {"ref":"c3","name":"C","attributes":[],"evidence":{"label":"C"}},
                                  {"ref":"c4","name":"D","attributes":[],"evidence":{"label":"D"}}
                                ],"relationships":[],"warnings":[],"confidence":0.9}
                                """));
                    } else {
                        json(exchange, 200, completion("""
                                {"relationships":[
                                  {"sourceRef":"c1","targetRef":"c99","type":"ASSOCIATION","evidence":{"label":"A-X"}}
                                ],"warnings":[],"confidence":0.8}
                                """));
                    }
                }
        );
        server.start();

        try {
            AssistantPlanningException exception = assertThrows(
                    AssistantPlanningException.class,
                    () -> twoPassGateway(JsonMapper.builder().build(), baseUrl(server))
                            .analyze(image(), emptyContext())
            );
            assertTrue(exception.getMessage().contains("ref de clase no confirmado"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsTruncatedOutputWithoutJsonRepair() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> json(
                        exchange,
                        200,
                        "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"classes\\\":[\"}}]}"
                )
        );
        server.start();

        try {
            AssistantPlanningException exception = assertThrows(
                    AssistantPlanningException.class,
                    () -> gateway(JsonMapper.builder().build(), baseUrl(server))
                            .analyze(image(), emptyContext())
            );
            assertTrue(exception.getMessage().contains("truncada"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMalformedProposalEvenWhenHttpIsSuccessful() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> json(
                        exchange,
                        200,
                        "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"not-json\"}}]}"
                )
        );
        server.start();

        try {
            AssistantPlanningException exception = assertThrows(
                    AssistantPlanningException.class,
                    () -> gateway(JsonMapper.builder().build(), baseUrl(server))
                            .analyze(image(), emptyContext())
            );
            assertTrue(exception.getMessage().contains("sin reparacion heuristica"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesHttpFailureAsVisionPlanningError() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> json(exchange, 500, "{\"error\":\"vision failed\"}")
        );
        server.start();

        try {
            AssistantPlanningException exception = assertThrows(
                    AssistantPlanningException.class,
                    () -> gateway(JsonMapper.builder().build(), baseUrl(server))
                            .analyze(image(), emptyContext())
            );
            assertTrue(exception.getMessage().contains("HTTP 500"));
        } finally {
            server.stop(0);
        }
    }


    private LlamaCppVisionModelGateway twoPassGateway(JsonMapper mapper, String url) {
        return new LlamaCppVisionModelGateway(
                mapper,
                new VisionPromptBuilder(),
                url,
                "vision-model",
                5L,
                800,
                true,
                4,
                600
        );
    }

    private String completion(String content) {
        try {
            JsonMapper mapper = JsonMapper.builder().build();
            return mapper.writeValueAsString(java.util.Map.of(
                    "choices", List.of(java.util.Map.of(
                            "finish_reason", "stop",
                            "message", java.util.Map.of("content", content.strip())
                    ))
            ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LlamaCppVisionModelGateway gateway(JsonMapper mapper, String url) {
        return new LlamaCppVisionModelGateway(
                mapper,
                new VisionPromptBuilder(),
                url,
                "vision-model",
                5L,
                800
        );
    }

    private VisionNormalizedImage image() {
        return new VisionNormalizedImage(
                "diagram.png",
                "image/png",
                "image/png",
                new byte[]{1, 2, 3, 4},
                320,
                200,
                "sha",
                false
        );
    }

    private VisionProjectContext emptyContext() {
        return new VisionProjectContext(UUID.randomUUID(), 0L, List.of());
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
