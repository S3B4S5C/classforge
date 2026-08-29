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
