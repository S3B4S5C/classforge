package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.groq.GroqApiException;
import com.classforge.assistant.groq.GroqClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GroqVisionModelGatewayTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsAuthenticatedImageAndSupportedSchemaWithoutLeakingProjectIdsOrSecret() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        GroqClient client = serve(exchange -> {
            assertEquals("Bearer server-secret", exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(body.contains("server-secret"));
            requests.add(mapper.readTree(body));
            respond(exchange, 200, completion(firstPass()));
        });
        UUID projectId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        VisionUmlProposal result = gateway(client, false).analyze(image(), new VisionProjectContext(
                projectId, 1L, List.of(new VisionExistingClassContext(classId, "Cliente", List.of("nombre")))));

        assertEquals("Cliente", result.safeClasses().getFirst().name());
        JsonNode request = requests.getFirst();
        assertEquals("qwen/qwen3.8-27b", request.get("model").asString());
        assertEquals(800, request.get("max_completion_tokens").asInt());
        assertNull(request.get("max_tokens"));
        assertNull(request.get("chat_template_kwargs"));
        assertNotNull(request.get("reasoning_effort"), "Groq must disable thinking for bounded JSON extraction");
        assertEquals("none", request.get("reasoning_effort").asString());
        JsonNode format = request.get("response_format");
        assertEquals("json_schema", format.get("type").asString());
        assertFalse(format.get("json_schema").get("strict").asBoolean());
        assertFalse(format.get("json_schema").get("schema").get("additionalProperties").asBoolean());
        JsonNode schema = format.get("json_schema").get("schema");
        JsonNode classSchema = schema.get("properties").get("classes").get("items");
        JsonNode attributeSchema = classSchema.get("properties").get("attributes").get("items");
        JsonNode relationshipSchema = schema.get("properties").get("relationships").get("items");
        assertLabelOnlyEvidence(classSchema.get("properties").get("evidence"));
        assertLabelOnlyEvidence(attributeSchema.get("properties").get("evidence"));
        assertLabelOnlyEvidence(relationshipSchema.get("properties").get("evidence"));
        assertEquals("[\"classes\",\"relationships\",\"warnings\"]", schema.get("required").toString());
        assertEquals("[\"ref\",\"name\",\"attributes\",\"evidence\"]", classSchema.get("required").toString());
        assertEquals("[\"name\",\"evidence\"]", attributeSchema.get("required").toString());
        assertEquals("[\"sourceRef\",\"targetRef\",\"type\",\"evidence\"]", relationshipSchema.get("required").toString());
        assertTrue(request.get("messages").get(0).get("content").asString().contains("No incluyas x, y, width ni height en evidence"));
        assertEquals("data:image/png;base64,AQIDBA==", request.get("messages").get(1)
                .get("content").get(1).get("image_url").get("url").asString());
        String body = request.toString();
        assertFalse(body.contains(projectId.toString()));
        assertFalse(body.contains(classId.toString()));
    }

    @Test
    void preservesClosedReferencesAndTokenBudgetInDenseRelationshipPass() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        GroqClient client = serve(exchange -> {
            requests.add(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, 200, completion(requests.size() == 1 ? firstPass() : """
                    {"relationships":[{"sourceRef":"c1","targetRef":"c2","type":"ASSOCIATION","evidence":{"label":"Cliente-Pedido"}}],"warnings":[],"confidence":0.8}
                    """));
        });
        VisionUmlProposal result = gateway(client, true).analyze(image(), context());

        assertEquals(2, requests.size());
        assertEquals("c2", result.safeRelationships().getFirst().targetRef());
        assertEquals(0.8, result.confidence(), 0.0001);
        JsonNode second = requests.get(1);
        assertEquals(600, second.get("max_completion_tokens").asInt());
        assertEquals("VisionRelationshipPassProposal", second.get("response_format")
                .get("json_schema").get("schema").get("title").asString());
        JsonNode relationshipSchema = second.get("response_format").get("json_schema").get("schema")
                .get("properties").get("relationships").get("items");
        assertLabelOnlyEvidence(relationshipSchema.get("properties").get("evidence"));
        assertEquals("[\"sourceRef\",\"targetRef\",\"type\",\"evidence\"]", relationshipSchema.get("required").toString());
        assertTrue(second.get("messages").get(1).get("content").get(0).get("text").asString().contains("c1 = \"Cliente\""));
    }

    @Test
    void rejectsMalformedJsonWithoutRepairOrAnotherProviderCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqClient client = serve(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, completion("```json\n{}\n```"));
        });

        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway(client, true).analyze(image(), context()));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsTruncatedOutputBeforeParsingOrSecondPass() throws Exception {
        GroqClient client = serve(exchange -> respond(exchange, 200,
                mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "length",
                        "message", Map.of("content", firstPass())))))));
        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway(client, true).analyze(image(), context()));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
    }

    @Test
    void rejectsRelationshipReferencesOutsideConfirmedClasses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqClient client = serve(exchange -> respond(exchange, 200, completion(calls.incrementAndGet() == 1
                ? firstPass() : """
                {"relationships":[{"sourceRef":"c1","targetRef":"invented","type":"ASSOCIATION"}],"warnings":[]}
                """)));
        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway(client, true).analyze(image(), context()));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
        assertEquals(2, calls.get());
    }

    @Test
    void preservesQuotaStatusAndRetryAfterWithoutAutomaticRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqClient client = serve(exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "17");
            respond(exchange, 429, "{\"error\":{\"message\":\"server-secret\"}}");
        });
        GroqApiException failure = assertThrows(GroqApiException.class,
                () -> gateway(client, true).analyze(image(), context()));
        assertEquals(429, failure.statusCode());
        assertEquals(17, failure.retryAfterSeconds());
        assertFalse(failure.getMessage().contains("server-secret"));
        assertEquals(1, calls.get());
    }

    @Test
    void stillRejectsProviderPartialBoundingBoxThroughRealJsonAndGroundingPipeline() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqClient client = serve(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, completion("""
                    {"classes":[{"ref":"c1","name":"Cliente","attributes":[],"evidence":{"label":"Cliente","x":10,"y":20}}],"relationships":[],"warnings":[]}
                    """));
        });
        VisionUmlProposal proposal = gateway(client, false).analyze(image(), context());
        assertEquals(10, proposal.safeClasses().getFirst().evidence().x());
        assertNull(proposal.safeClasses().getFirst().evidence().width());
        AssistantPlanningException failure = assertThrows(AssistantPlanningException.class,
                () -> new VisionProposalGroundingValidator().validate(proposal));
        assertTrue(failure.getMessage().contains("completos o ninguno"));
        assertEquals(1, calls.get());
    }

    private void assertLabelOnlyEvidence(JsonNode schema) {
        assertEquals("[\"label\"]", schema.get("required").toString());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertNotNull(schema.get("properties").get("label"));
        assertNotNull(schema.get("properties").get("confidence"));
        for (String coordinate : List.of("x", "y", "width", "height")) {
            assertNull(schema.get("properties").get(coordinate), "Groq semantic evidence must not request " + coordinate);
        }
    }

    private GroqVisionModelGateway gateway(GroqClient client, boolean twoPass) {
        return new GroqVisionModelGateway(mapper, new VisionPromptBuilder(), client,
                "qwen/qwen3.8-27b", 800, twoPass, 2, 600);
    }

    private GroqClient serve(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", handler);
        server.start();
        return new GroqClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "server-secret", 5000);
    }

    private String completion(String content) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content)))));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String firstPass() {
        return """
                {"summary":"Clases visibles","classes":[
                  {"ref":"c1","name":"Cliente","attributes":[],"evidence":{"label":"Cliente"}},
                  {"ref":"c2","name":"Pedido","attributes":[],"evidence":{"label":"Pedido"}}
                ],"relationships":[],"warnings":[],"confidence":0.9}
                """;
    }

    private VisionNormalizedImage image() {
        return new VisionNormalizedImage("uml.png", "image/png", "image/png", new byte[]{1, 2, 3, 4}, 320, 200, "test", false);
    }

    private VisionProjectContext context() {
        return new VisionProjectContext(UUID.randomUUID(), 1L, List.of());
    }
}
