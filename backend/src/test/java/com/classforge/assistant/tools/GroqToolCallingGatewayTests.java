package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantEntityReferenceResolver;
import com.classforge.assistant.AssistantIntentHintResolver;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.groq.GroqApiException;
import com.classforge.assistant.groq.GroqClient;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroqToolCallingGatewayTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final DynamicUmlToolCatalog catalogs = new DynamicUmlToolCatalog(new AssistantIntentHintResolver());
    private final AtomicReference<String> captured = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void sendsRealGroundedCatalogAndResolvesNativeCallWithoutMutatingSnapshot() throws Exception {
        UUID classId = UUID.randomUUID();
        var document = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(classId, "Cliente", List.of())), List.of()), null);
        var catalog = catalogs.buildForTools(document, List.of(AssistantToolName.RENAME_CLASS));
        start(200, completion("rename_class", "{\"existing_class\":\"Cliente\",\"new_name\":\"Comprador\"}"));

        var calls = gateway().call("Renombra Cliente a Comprador", document, catalog);
        var resolution = new UmlToolCallResolver(new AssistantEntityReferenceResolver(), new AssistantLiteralArgumentBinder())
                .resolve("Renombra Cliente a Comprador", calls, catalog, document);

        assertEquals(1, calls.size());
        assertEquals(AssistantToolName.RENAME_CLASS, calls.getFirst().name());
        assertEquals(classId, resolution.references().getFirst().elementId());
        assertEquals("Cliente", document.umlModel().classes().getFirst().name());
        JsonNode payload = mapper.readTree(captured.get());
        assertEquals("openai/gpt-oss-120b", payload.get("model").asString());
        assertEquals("required", payload.get("tool_choice").asString());
        assertEquals(0.0d, payload.get("temperature").asDouble());
        assertFalse(payload.get("parallel_tool_calls").asBoolean());
        assertTrue(payload.has("max_completion_tokens"));
        assertFalse(payload.has("max_tokens"));
        var parameters = payload.get("tools").get(0).get("function").get("parameters");
        assertEquals("Cliente", parameters.get("properties").get("existing_class").get("enum").get(0).asString());
        assertFalse(parameters.get("additionalProperties").asBoolean());
        assertEquals(1, requests.get(), "No llama.cpp capability probe or implicit retry is needed");
    }

    @Test
    void preservesAcceptedToolCallsAndResultsWithoutDuplicatingActiveQuestion() throws Exception {
        start(200, completion("create_class", "{\"name\":\"Factura\"}"));
        var catalog = catalogs.buildForTools(ProjectDocument.empty(), List.of(AssistantToolName.CREATE_CLASS));
        var prior = new AssistantToolInvocation("accepted-1", AssistantToolName.CREATE_CLASS, mapper.readTree("{\"name\":\"Cliente\"}"));

        gateway().call("Crea Cliente y Factura", ProjectDocument.empty(), catalog,
                List.of(new AssistantToolConversationTurn(List.of(prior), List.of("Cliente preparado en preview"))));

        var messages = mapper.readTree(captured.get()).get("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).get("role").asString());
        assertEquals("Crea Cliente y Factura", messages.get(1).get("content").asString());
        assertEquals("assistant", messages.get(2).get("role").asString());
        assertEquals("accepted-1", messages.get(2).get("tool_calls").get(0).get("id").asString());
        assertEquals("{\"name\":\"Cliente\"}", messages.get(2).get("tool_calls").get(0).get("function").get("arguments").asString());
        assertEquals("tool", messages.get(3).get("role").asString());
        assertEquals("accepted-1", messages.get(3).get("tool_call_id").asString());
        assertEquals("Cliente preparado en preview", messages.get(3).get("content").asString());
    }

    @Test
    void routingPreservesOrderedExecutableStepsFromActualTinyCatalog() throws Exception {
        start(200, completion("route_uml_request", "{\"steps\":[\"create_class\",\"add_attributes\",\"create_association\"]}"));
        var calls = gateway().call("Crea Cliente con email y relacionalo con Factura", ProjectDocument.empty(), catalogs.routingCatalog());
        assertEquals(AssistantToolName.ROUTE_REQUEST, calls.getFirst().name());
        assertEquals("create_class", calls.getFirst().arguments().get("steps").get(0).asString());
        assertEquals("create_association", calls.getFirst().arguments().get("steps").get(2).asString());
        assertEquals(1, mapper.readTree(captured.get()).get("tools").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "[]", "{}", "{\"name\":12}", "{\"name\":\"\"}", "{\"name\":\"Cliente\",\"unexpected\":true}"})
    void rejectsMalformedOrSchemaInvalidArgumentsWithoutAnotherProviderCall(String arguments) throws Exception {
        start(200, completion("create_class", arguments));
        var catalog = catalogs.buildForTools(ProjectDocument.empty(), List.of(AssistantToolName.CREATE_CLASS));
        assertThrows(AssistantPlanningException.class, () -> gateway().call("Crea Cliente", ProjectDocument.empty(), catalog));
        assertEquals(1, requests.get());
    }

    @Test
    void rejectsExistingReferenceOutsideGroundedEnum() throws Exception {
        var document = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(UUID.randomUUID(), "Cliente", List.of())), List.of()), null);
        start(200, completion("rename_class", "{\"existing_class\":\"Inventada\",\"new_name\":\"Comprador\"}"));
        var catalog = catalogs.buildForTools(document, List.of(AssistantToolName.RENAME_CLASS));
        assertThrows(AssistantPlanningException.class, () -> gateway().call("Renombra Cliente", document, catalog));
    }

    @Test
    void rejectsToolOutsideCurrentCatalog() throws Exception {
        start(200, completion("delete_class", "{\"existing_class\":\"Cliente\"}"));
        var catalog = catalogs.buildForTools(ProjectDocument.empty(), List.of(AssistantToolName.CREATE_CLASS));
        assertThrows(AssistantPlanningException.class, () -> gateway().call("Crea Cliente", ProjectDocument.empty(), catalog));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":\"Ya lo creé\"}}]}"})
    void rejectsResponsesWithoutNativeTools(String envelope) throws Exception {
        start(200, envelope);
        var catalog = catalogs.buildForTools(ProjectDocument.empty(), List.of(AssistantToolName.CREATE_CLASS));
        assertThrows(AssistantPlanningException.class, () -> gateway().call("Crea Cliente", ProjectDocument.empty(), catalog));
        assertEquals(1, requests.get());
    }

    @Test
    void preservesQuotaErrorAndRetryAfterWithoutAutomaticRetries() throws Exception {
        start(429, "{\"error\":{\"message\":\"provider-private-detail\"}}");
        var catalog = catalogs.buildForTools(ProjectDocument.empty(), List.of(AssistantToolName.CREATE_CLASS));
        var error = assertThrows(GroqApiException.class, () -> gateway().call("Crea Cliente", ProjectDocument.empty(), catalog));
        assertEquals(429, error.statusCode());
        assertEquals(17, error.retryAfterSeconds());
        assertFalse(error.getMessage().contains("provider-private-detail"));
        assertEquals(1, requests.get());
    }

    private GroqToolCallingGateway gateway() {
        var client = new GroqClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-key", 2000);
        return new GroqToolCallingGateway(client, mapper, "openai/gpt-oss-120b", 2048, 120000);
    }

    private String completion(String name, String arguments) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "tool_calls", "message", Map.of(
                "role", "assistant", "tool_calls", List.of(Map.of("id", "call-1", "type", "function",
                        "function", Map.of("name", name, "arguments", arguments))))))));
    }

    private void start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try { exchange.sendResponseHeaders(404, -1); } finally { exchange.close(); }
        });
        server.createContext("/openai/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (status == 429) exchange.getResponseHeaders().set("Retry-After", "17");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
    }
}
