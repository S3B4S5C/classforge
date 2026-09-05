package com.classforge.assistant.vision;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaCppVisionHybridModelGatewayTests {

    @Test
    void reportsBoundedPartialContentWhenSingletonRelationshipResponseTruncates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                    {"choices":[{"finish_reason":"length","message":{"content":"PARTIAL_CONTENT","reasoning_content":"PARTIAL_REASONING"}}],"usage":{"completion_tokens":512}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            LlamaCppVisionHybridModelGateway gateway = new LlamaCppVisionHybridModelGateway(
                    JsonMapper.builder().build(), new VisionHybridPromptBuilder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "vision-model", 10, 1200, 512, 128
            );

            VisionModelGatewayException exception = assertThrows(
                    VisionModelGatewayException.class,
                    () -> gateway.classifyRelationship(image(), edge(), classes())
            );

            assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, exception.reason());
            assertTrue(exception.getMessage().contains("anotacion-local-de-relacion:E4"));
            assertTrue(exception.getMessage().contains("completionTokens=512"));
            assertTrue(exception.getMessage().contains("PARTIAL_CONTENT"));
            assertTrue(exception.getMessage().contains("PARTIAL_REASONING"));
        } finally {
            server.stop(0);
        }
    }

    private VisionNormalizedImage image() {
        return new VisionNormalizedImage("E4.png", "image/png", "image/png", new byte[]{1}, 1, 1, "test", true);
    }

    private VisionGeometryEdgeCandidate edge() {
        return new VisionGeometryEdgeCandidate("E4", "B1", "B2", "c1", "c2", 1.0, 0, 0, 1, 1, 0, 0, 1, 1);
    }

    private List<VisionClassProposal> classes() {
        return List.of(
                new VisionClassProposal("c1", "A", List.of(), null),
                new VisionClassProposal("c2", "B", List.of(), null)
        );
    }
}
