package com.classforge.assistant.vision;

import com.classforge.assistant.groq.GroqApiException;
import com.classforge.assistant.groq.GroqClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroqVisionHybridModelGatewayTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void sendsHybridPanelWithSupportedSchemaAndParsesClosedEdge() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        GroqVisionHybridModelGateway gateway = serve(200, """
                {"edges":[{"edgeId":"E4","type":"ASSOCIATION","markerAt":"NONE","evidenceLabel":"Cliente-Pedido","confidence":0.9}],"warnings":[],"confidence":0.9}
                """, request, new AtomicInteger());
        VisionHybridRelationshipClassificationProposal result = gateway.classifyRelationship(image(), edge(), classes());
        assertEquals("E4", result.edges().getFirst().edgeId());
        assertEquals("ASSOCIATION", result.edges().getFirst().type());
        assertEquals("qwen/qwen3.8-27b", request.get().get("model").asString());
        assertEquals(512, request.get().get("max_completion_tokens").asInt());
        assertNotNull(request.get().get("reasoning_effort"), "512-token classification must disable thinking");
        assertEquals("none", request.get().get("reasoning_effort").asString());
        assertNull(request.get().get("max_tokens"));
        assertNull(request.get().get("chat_template_kwargs"));
        assertEquals("json_schema", request.get().get("response_format").get("type").asString());
        assertEquals(mapper.readTree(VisionHybridRelationshipJsonSchema.jsonForEdge("E4")),
                request.get().get("response_format").get("json_schema").get("schema"));
        assertEquals("data:image/png;base64,AQ==", request.get().get("messages").get(1).get("content")
                .get(1).get("image_url").get("url").asString());
    }

    @Test
    void rejectsHybridEdgeOutsideRequestedPanel() throws Exception {
        GroqVisionHybridModelGateway gateway = serve(200, "{\"edges\":[{\"edgeId\":\"invented\"}]}",
                new AtomicReference<>(), new AtomicInteger());
        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway.classifyRelationship(image(), edge(), classes()));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
    }

    @Test
    void retainsMultiplicityEndpointValidationAndNoneNormalization() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        GroqVisionHybridModelGateway gateway = serve(200,
                "{\"edgeId\":\"E4\",\"endpoint\":\"A\",\"rawLabel\":\"NONE\",\"confidence\":0.9}",
                request, new AtomicInteger());
        VisionHybridMultiplicityTranscription result = gateway.transcribeMultiplicity(image(), edge(), VisionHybridEndpoint.A, classes().getFirst());
        assertNull(result.rawLabel());
        assertEquals(128, request.get().get("max_completion_tokens").asInt());
        assertNotNull(request.get().get("reasoning_effort"), "128-token transcription must disable thinking");
        assertEquals("none", request.get().get("reasoning_effort").asString());
        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway.transcribeMultiplicity(image(), edge(), VisionHybridEndpoint.B, classes().getLast()));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
    }

    @Test
    void rejectsMultiplicityOwnershipOutsideVisibleCandidates() throws Exception {
        GroqVisionHybridModelGateway gateway = serve(200,
                "{\"edgeId\":\"E4\",\"endpoint\":\"A\",\"owner\":\"invented\",\"confidence\":0.9}",
                new AtomicReference<>(), new AtomicInteger());
        VisionModelGatewayException failure = assertThrows(VisionModelGatewayException.class,
                () -> gateway.attributeMultiplicity(image(), edge(), VisionHybridEndpoint.A, classes().getFirst(), "1", List.of("E5")));
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, failure.reason());
    }

    @Test
    void preservesHybridQuotaExceptionInsteadOfWrappingItAsInvalidOutput() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqVisionHybridModelGateway gateway = serve(429, "quota", new AtomicReference<>(), calls);
        GroqApiException failure = assertThrows(GroqApiException.class,
                () -> gateway.classifyRelationship(image(), edge(), classes()));
        assertEquals(429, failure.statusCode());
        assertEquals(17, failure.retryAfterSeconds());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hybridPipelinePropagatesQuotaEvenWhenSemanticFallbackIsEnabled(boolean fallback) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GroqVisionHybridModelGateway gateway = serve(429, "quota", new AtomicReference<>(), calls);
        VisionHybridAnalyzer analyzer = new VisionHybridAnalyzer(
                (image, context) -> new VisionUmlProposal("classes", classes(), List.of(), List.of(), 0.9),
                gateway,
                (image, count) -> new UmlClassRegionDetection(List.of(), new byte[]{1}, new byte[]{1}),
                new VisionGeometryClassMappingValidator(), new VisionHybridImageFactory(),
                (image, regions) -> { throw new AssertionError("Geometry must not run after provider failure"); },
                new VisionRelationshipEvidenceSheetRenderer(), new VisionHybridProposalAssembler(), true, 2, fallback
        );
        GroqApiException failure = assertThrows(GroqApiException.class, () -> analyzer.analyze(image(), null));
        assertEquals(429, failure.statusCode());
        assertEquals(17, failure.retryAfterSeconds());
        assertEquals(1, calls.get());
    }

    private GroqVisionHybridModelGateway serve(int status, String content, AtomicReference<JsonNode> request, AtomicInteger calls) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            assertEquals("Bearer server-secret", exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] bytes = (status == 200 ? mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content))))) : content).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "17");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new GroqVisionHybridModelGateway(mapper, new VisionHybridPromptBuilder(),
                new GroqClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "server-secret", 5000),
                "qwen/qwen3.8-27b", 1200, 512, 128);
    }

    private VisionNormalizedImage image() {
        return new VisionNormalizedImage("E4.png", "image/png", "image/png", new byte[]{1}, 1, 1, "test", true);
    }

    private VisionGeometryEdgeCandidate edge() {
        return new VisionGeometryEdgeCandidate("E4", "B1", "B2", "c1", "c2", 1.0, 0, 0, 1, 1, 0, 0, 1, 1);
    }

    private List<VisionClassProposal> classes() {
        return List.of(new VisionClassProposal("c1", "Cliente", List.of(), null), new VisionClassProposal("c2", "Pedido", List.of(), null));
    }
}
