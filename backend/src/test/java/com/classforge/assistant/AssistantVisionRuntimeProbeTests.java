package com.classforge.assistant;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantVisionRuntimeProbeTests {

    @Test
    void visionIsReadyOnlyWhenModelAndVisionModalityMatch() throws Exception {
        HttpServer server = server();
        standardHealth(server);
        server.createContext(
                "/v1/models",
                exchange -> json(
                        exchange,
                        200,
                        "{\"data\":[{\"id\":\"vision-model\",\"owned_by\":\"llamacpp\"}]}"
                )
        );
        server.createContext(
                "/props",
                exchange -> json(exchange, 200, "{\"modalities\":{\"vision\":true,\"audio\":false}}")
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(baseUrl(server), "vision-model");
            assertTrue(status.available());
            assertEquals("READY", status.state());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void textOnlyLlamaOnVisionPortIsIncompatible() throws Exception {
        HttpServer server = server();
        standardHealth(server);
        server.createContext(
                "/v1/models",
                exchange -> json(
                        exchange,
                        200,
                        "{\"data\":[{\"id\":\"vision-model\",\"owned_by\":\"llamacpp\"}]}"
                )
        );
        server.createContext(
                "/props",
                exchange -> json(exchange, 200, "{\"modalities\":{\"vision\":false}}")
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(baseUrl(server), "vision-model");
            assertFalse(status.available());
            assertEquals("INCOMPATIBLE", status.state());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wrongModelAliasIsMismatchBeforeVisionCapability() throws Exception {
        HttpServer server = server();
        standardHealth(server);
        server.createContext(
                "/v1/models",
                exchange -> json(
                        exchange,
                        200,
                        "{\"data\":[{\"id\":\"local-model\",\"owned_by\":\"llamacpp\"}]}"
                )
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(baseUrl(server), "vision-model");
            assertFalse(status.available());
            assertEquals("MISMATCH", status.state());
        } finally {
            server.stop(0);
        }
    }

    private AssistantVisionRuntimeProbe probe() {
        return new AssistantVisionRuntimeProbe(JsonMapper.builder().build());
    }

    private void standardHealth(HttpServer server) {
        server.createContext(
                "/health",
                exchange -> json(exchange, 200, "{\"status\":\"ok\"}")
        );
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
