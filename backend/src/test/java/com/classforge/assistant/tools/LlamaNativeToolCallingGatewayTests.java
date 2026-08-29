package com.classforge.assistant.tools;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaNativeToolCallingGatewayTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesOpenAiNativeToolsAndParsesToolCalls() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicReference<String> requestBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(
                    exchange,
                    200,
                    "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"HistorialClinco\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
            );
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );

        AssistantToolCatalog catalog = new AssistantToolCatalog(
                List.of(
                        new AssistantToolDefinition(
                                AssistantToolName.CREATE_CLASS,
                                "Create class",
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of("name", Map.of("type", "string")),
                                        "required", List.of("name"),
                                        "additionalProperties", false
                                )
                        )
                ),
                Map.of(),
                Map.of(),
                Map.of()
        );

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea HistorialClinco",
                new ProjectDocument(
                        ProjectDocument.CURRENT_SCHEMA_VERSION,
                        new UmlModel(List.of(), List.of()),
                        new DiagramLayout(Map.of())
                ),
                catalog
        );

        assertEquals(1, calls.size());
        assertEquals(AssistantToolName.CREATE_CLASS, calls.getFirst().name());
        assertEquals("HistorialClinco", calls.getFirst().arguments().get("name").asString());

        JsonNode sent = jsonMapper.readTree(requestBody.get());
        assertEquals("required", sent.get("tool_choice").asString());
        assertTrue(sent.get("tools").isArray());
        assertEquals("create_class", sent.get("tools").get(0).get("function").get("name").asString());
        assertEquals("0.0", sent.get("temperature").asString());
    }



    @Test
    void routingRequestUsesTinySemanticCatalogAndParsesOrderedSteps() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicReference<String> requestBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(
                    exchange,
                    200,
                    "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"route_1\",\"type\":\"function\",\"function\":{\"name\":\"route_uml_request\",\"arguments\":\"{\\\"steps\\\":[\\\"create_class\\\",\\\"add_attributes\\\",\\\"create_association\\\"]}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
            );
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );
        DynamicUmlToolCatalog catalogs = new DynamicUmlToolCatalog(new com.classforge.assistant.AssistantIntentHintResolver());
        AssistantToolCatalog routing = catalogs.routingCatalog();

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea Cliente, agregale email y asociala con Factura",
                new ProjectDocument(
                        ProjectDocument.CURRENT_SCHEMA_VERSION,
                        new UmlModel(List.of(), List.of()),
                        new DiagramLayout(Map.of())
                ),
                routing
        );

        assertEquals(1, routing.definitions().size());
        assertEquals(AssistantToolName.ROUTE_REQUEST, routing.definitions().getFirst().name());
        assertEquals(AssistantToolName.ROUTE_REQUEST, calls.getFirst().name());
        assertEquals(3, calls.getFirst().arguments().get("steps").size());

        JsonNode sent = jsonMapper.readTree(requestBody.get());
        assertEquals(1, sent.get("tools").size());
        assertEquals("route_uml_request", sent.get("tools").get(0).get("function").get("name").asString());
        assertTrue(sent.get("messages").get(0).get("content").asString().contains("router de operaciones UML"));
    }

    @Test
    void sendsPriorToolCallsAndToolResultsForCompoundRounds() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicReference<String> requestBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(
                    exchange,
                    200,
                    "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"finish_plan\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
            );
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );

        AssistantToolCatalog catalog = new AssistantToolCatalog(
                List.of(
                        new AssistantToolDefinition(
                                AssistantToolName.FINISH_PLAN,
                                "Finish",
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "additionalProperties", false
                                )
                        )
                ),
                Map.of(),
                Map.of(),
                Map.of()
        );
        AssistantToolInvocation prior = new AssistantToolInvocation(
                "call_1",
                AssistantToolName.CREATE_CLASS,
                jsonMapper.readTree("{\"name\":\"Cliente\"}")
        );

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea Cliente y termina",
                new ProjectDocument(
                        ProjectDocument.CURRENT_SCHEMA_VERSION,
                        new UmlModel(List.of(), List.of()),
                        new DiagramLayout(Map.of())
                ),
                catalog,
                List.of(new AssistantToolConversationTurn(
                        List.of(prior),
                        List.of("PLANNED_OK: Crear clase Cliente")
                ))
        );

        assertEquals(1, calls.size());
        assertEquals(AssistantToolName.FINISH_PLAN, calls.getFirst().name());

        JsonNode sent = jsonMapper.readTree(requestBody.get());
        JsonNode messages = sent.get("messages");
        assertTrue(messages.get(3) != null);
        assertEquals("assistant", messages.get(2).get("role").asString());
        assertEquals("call_1", messages.get(2).get("tool_calls").get(0).get("id").asString());
        assertEquals("create_class", messages.get(2).get("tool_calls").get(0).get("function").get("name").asString());
        assertEquals("tool", messages.get(3).get("role").asString());
        assertEquals("call_1", messages.get(3).get("tool_call_id").asString());
        assertEquals("PLANNED_OK: Crear clase Cliente", messages.get(3).get("content").asString());
    }


    @Test
    void retriesOnceWhenRequiredToolChoiceReturnsConversationalContent() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> secondRequest = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            int request = requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request == 1) {
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":\"Listo, cambie el nombre.\"},\"finish_reason\":\"stop\"}]}"
                );
            } else {
                secondRequest.set(body);
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_retry\",\"type\":\"function\",\"function\":{\"name\":\"rename_class\",\"arguments\":\"{\\\"existing_class\\\":\\\"Veterinario\\\",\\\"new_name\\\":\\\"DoctorVeterinario\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
                );
            }
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );

        AssistantToolCatalog catalog = new AssistantToolCatalog(
                List.of(new AssistantToolDefinition(
                        AssistantToolName.RENAME_CLASS,
                        "Rename class",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "existing_class", Map.of("type", "string", "enum", List.of("Veterinario")),
                                        "new_name", Map.of("type", "string")
                                ),
                                "required", List.of("existing_class", "new_name"),
                                "additionalProperties", false
                        )
                )),
                Map.of("Veterinario", java.util.UUID.randomUUID()),
                Map.of(),
                Map.of()
        );

        List<AssistantToolInvocation> calls = gateway.call(
                "VETERINARIO ahora se llama DoctorVeterinario",
                new ProjectDocument(
                        ProjectDocument.CURRENT_SCHEMA_VERSION,
                        new UmlModel(List.of(), List.of()),
                        new DiagramLayout(Map.of())
                ),
                catalog
        );

        assertEquals(2, requests.get());
        assertEquals(1, calls.size());
        assertEquals(AssistantToolName.RENAME_CLASS, calls.getFirst().name());

        JsonNode retry = jsonMapper.readTree(secondRequest.get());
        assertEquals("512", retry.get("max_tokens").asString());
        assertTrue(retry.get("messages").get(1).get("content").asString().contains("exactamente una tool_call"));
    }


    @Test
    void ignoresTruncatedTrailingBurstAfterFirstValidExposedToolCall() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicInteger requests = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            json(
                    exchange,
                    200,
                    "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
                            + "{\"id\":\"call_ok\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"Cliente\\\"}\"}},"
                            + "{\"id\":\"call_tail\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"ClienteDuplicado\"}}"
                            + "]},\"finish_reason\":\"length\"}]}"
            );
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );
        AssistantToolCatalog catalog = singleCreateClassCatalog();

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea Cliente",
                emptyDocument(),
                catalog
        );

        assertEquals(1, requests.get());
        assertEquals(1, calls.size());
        assertEquals("Cliente", calls.getFirst().arguments().get("name").asString());
    }

    @Test
    void retriesWithLargerBudgetWhenFirstExposedToolArgumentsAreTruncated() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> retryRequest = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            int request = requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request == 1) {
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_cut\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"Clien\"}}]},\"finish_reason\":\"length\"}]}"
                );
            } else {
                retryRequest.set(body);
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_retry\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"Cliente\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
                );
            }
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea Cliente",
                emptyDocument(),
                singleCreateClassCatalog()
        );

        assertEquals(2, requests.get());
        assertEquals("Cliente", calls.getFirst().arguments().get("name").asString());
        JsonNode retry = jsonMapper.readTree(retryRequest.get());
        assertEquals("512", retry.get("max_tokens").asString());
        assertTrue(retry.get("messages").get(1).get("content").asString().contains("quedo truncada"));
    }


    @Test
    void retriesWhenLlamaHttpEnvelopeItselfIsTruncated() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> retryRequest = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/props", exchange -> json(
                exchange,
                200,
                "{\"chat_template_caps\":{\"supports_tools\":true,\"supports_tool_calls\":true}}"
        ));
        server.createContext("/v1/chat/completions", exchange -> {
            int request = requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request == 1) {
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"cut\""
                );
            } else {
                retryRequest.set(body);
                json(
                        exchange,
                        200,
                        "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_retry\",\"type\":\"function\",\"function\":{\"name\":\"create_class\",\"arguments\":\"{\\\"name\\\":\\\"Cliente\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"
                );
            }
        });
        server.start();

        LlamaNativeToolCallingGateway gateway = new LlamaNativeToolCallingGateway(
                jsonMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "local-model"
        );

        List<AssistantToolInvocation> calls = gateway.call(
                "Crea Cliente",
                emptyDocument(),
                singleCreateClassCatalog()
        );

        assertEquals(2, requests.get());
        assertEquals("Cliente", calls.getFirst().arguments().get("name").asString());
        JsonNode retry = jsonMapper.readTree(retryRequest.get());
        assertEquals("512", retry.get("max_tokens").asString());
        assertTrue(retry.get("messages").get(1).get("content").asString().contains("respuesta JSON anterior"));
    }

    private AssistantToolCatalog singleCreateClassCatalog() {
        return new AssistantToolCatalog(
                List.of(new AssistantToolDefinition(
                        AssistantToolName.CREATE_CLASS,
                        "Create class",
                        Map.of(
                                "type", "object",
                                "properties", Map.of("name", Map.of("type", "string")),
                                "required", List.of("name"),
                                "additionalProperties", false
                        )
                )),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private ProjectDocument emptyDocument() {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(), List.of()),
                new DiagramLayout(Map.of())
        );
    }

    private void json(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
