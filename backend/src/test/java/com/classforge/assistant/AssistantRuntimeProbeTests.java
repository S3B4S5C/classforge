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

class AssistantRuntimeProbeTests {

    @Test
    void genericHealthyServiceDoesNotMasqueradeAsLlamaCpp() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/health",
                exchange -> json(exchange, 200, "{\"status\":\"ok\"}")
        );
        server.createContext(
                "/v1/models",
                exchange -> json(
                        exchange,
                        200,
                        "{\"object\":\"list\",\"data\":[{\"id\":\"other\",\"owned_by\":\"other-runtime\"}]}"
                )
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(
                    "llama.cpp",
                    baseUrl(server)
            );

            assertFalse(status.available());
            assertEquals("MISMATCH", status.state());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void llamaCppNeedsLlamaModelIdentityAfterHealth() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/health",
                exchange -> json(exchange, 200, "{\"status\":\"ok\"}")
        );
        server.createContext(
                "/v1/models",
                exchange -> json(
                        exchange,
                        200,
                        "{\"object\":\"list\",\"data\":[{\"id\":\"local-model\",\"owned_by\":\"llamacpp\"}]}"
                )
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(
                    "llama.cpp",
                    baseUrl(server)
            );

            assertTrue(status.available());
            assertEquals("READY", status.state());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void whisperCppRequiresItsServerIdentity() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/health",
                exchange -> {
                    exchange.getResponseHeaders().add(
                            "Server",
                            "whisper.cpp"
                    );
                    json(exchange, 200, "{\"status\":\"ok\"}");
                }
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(
                    "whisper.cpp",
                    baseUrl(server)
            );

            assertTrue(status.available());
            assertEquals("READY", status.state());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void genericHealthyServiceDoesNotMasqueradeAsWhisperCpp() throws Exception {
        HttpServer server = server();
        server.createContext(
                "/health",
                exchange -> json(exchange, 200, "{\"status\":\"ok\"}")
        );
        server.createContext(
                "/",
                exchange -> text(exchange, 200, "Generic local service")
        );
        server.start();

        try {
            AssistantRuntimeStatus status = probe().probe(
                    "whisper.cpp",
                    baseUrl(server)
            );

            assertFalse(status.available());
            assertEquals("MISMATCH", status.state());
        } finally {
            server.stop(0);
        }
    }

    private AssistantRuntimeProbe probe() {
        return new AssistantRuntimeProbe(
                JsonMapper.builder().build()
        );
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void json(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        respond(exchange, status, body);
    }

    private void text(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/plain"
        );
        respond(exchange, status, body);
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
