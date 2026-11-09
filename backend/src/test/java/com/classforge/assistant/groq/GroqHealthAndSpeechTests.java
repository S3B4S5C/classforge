package com.classforge.assistant.groq;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GroqHealthAndSpeechTests {
    HttpServer server;
    String url;
    final AtomicInteger calls = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    GroqClient client(String key) { return new GroqClient(JsonMapper.builder().build(), url, key, 1000); }
    void response(String path, String text) {
        server.createContext("/openai/v1/" + path, exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
    }
    @Test void healthWithoutKeyIsNotConfiguredAndNeverCallsTheNetwork() {
        var status = new GroqHealthProbe(client(""), "text", "vision", "speech").probe();
        assertFalse(status.text().available()); assertFalse(status.speech().available()); assertFalse(status.vision().available());
        assertEquals("NOT_CONFIGURED", status.text().state()); assertEquals(0, calls.get());
    }
    @Test void healthChecksModelsAndCachesResultsInsteadOfClaimingAllModelsAreReady() {
        response("models", "{\"data\":[{\"id\":\"text\"},{\"id\":\"speech\"}]}");
        var probe = new GroqHealthProbe(client("test-only-key"), "text", "vision", "speech");
        var first = probe.probe();
        assertTrue(first.text().available()); assertTrue(first.speech().available()); assertFalse(first.vision().available());
        assertEquals("MODEL_UNAVAILABLE", first.vision().state());
        probe.probe(); assertEquals(1, calls.get());
    }
    @Test void speechUsesExistingGatewayContractAndRejectsEmptyTranscripts() {
        response("audio/transcriptions", "{\"text\":\"  Crea una clase Cliente  \"}");
        var gateway = new GroqSpeechToTextGateway(client("test-only-key"), "whisper-large-v3-turbo", "es");
        assertEquals("Crea una clase Cliente", gateway.transcribe(new byte[]{1,2,3}, "audio.wav"));
    }
    @Test void emptySpeechFailsInsteadOfPlanningAnEmptyOrInventedCommand() {
        response("audio/transcriptions", "{\"text\":\"  \"}");
        var gateway = new GroqSpeechToTextGateway(client("test-only-key"), "whisper-large-v3-turbo", "es");
        assertThrows(GroqApiException.class, () -> gateway.transcribe(new byte[]{1,2,3}, "audio.wav"));
    }
    @Test void wrappedProviderQuotaErrorReturns429AndRetryAfterWithoutInvalidatingUserAuthentication() {
        var quota = new GroqApiException(429, 21, "Cuota agotada");
        var wrapped = quota.withDiagnostic(com.classforge.assistant.AssistantPlanningStage.STT, "VOICE", null, null);
        var response = GroqErrorResponses.from(wrapped);
        assertNotNull(response); assertEquals(429, response.getStatusCode().value());
        assertEquals("21", response.getHeaders().getFirst("Retry-After"));
        assertEquals(503, GroqErrorResponses.from(new GroqApiException(401, 0, "Clave incorrecta")).getStatusCode().value());
    }
}
