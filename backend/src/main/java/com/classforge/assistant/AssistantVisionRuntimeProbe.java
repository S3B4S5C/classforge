package com.classforge.assistant;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class AssistantVisionRuntimeProbe {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public AssistantVisionRuntimeProbe(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public AssistantRuntimeStatus probe(
            String baseUrl,
            String expectedModel
    ) {
        long started = System.nanoTime();
        String name = "vision";

        try {
            String url = normalizeBaseUrl(baseUrl);
            HttpResponse<String> health = get(url + "/health");
            String state = stateFromBody(health.body());
            boolean ready = health.statusCode() >= 200
                    && health.statusCode() < 300
                    && "ok".equalsIgnoreCase(state);

            if (!ready) {
                return new AssistantRuntimeStatus(
                        name,
                        false,
                        health.statusCode() == 503 ? "LOADING" : "UNAVAILABLE",
                        elapsedMs(started),
                        "HTTP " + health.statusCode() + (state == null ? "" : " · " + state)
                );
            }

            if (!matchesModel(url, expectedModel)) {
                return new AssistantRuntimeStatus(
                        name,
                        false,
                        "MISMATCH",
                        elapsedMs(started),
                        "El puerto responde, pero no publica el modelo visual esperado."
                );
            }

            if (!supportsVision(url)) {
                return new AssistantRuntimeStatus(
                        name,
                        false,
                        "INCOMPATIBLE",
                        elapsedMs(started),
                        "llama.cpp responde, pero /props no anuncia modalities.vision=true."
                );
            }

            return new AssistantRuntimeStatus(
                    name,
                    true,
                    "READY",
                    elapsedMs(started),
                    "VLM multimodal listo"
            );
        } catch (Exception exception) {
            return new AssistantRuntimeStatus(
                    name,
                    false,
                    "UNREACHABLE",
                    elapsedMs(started),
                    "No responde el runtime visual local."
            );
        }
    }

    private boolean matchesModel(String baseUrl, String expectedModel) {
        try {
            HttpResponse<String> response = get(baseUrl + "/v1/models");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }

            JsonNode data = jsonMapper.readTree(response.body()).get("data");
            if (data == null || !data.isArray()) {
                return false;
            }

            String expected = expectedModel == null ? "" : expectedModel.trim();
            for (JsonNode model : data) {
                JsonNode ownedBy = model.get("owned_by");
                boolean llamaOwned = ownedBy != null
                        && ownedBy.asString().toLowerCase(Locale.ROOT).contains("llama");
                if (!llamaOwned) {
                    continue;
                }

                if (expected.isBlank()) {
                    return true;
                }

                JsonNode id = model.get("id");
                if (id != null && expected.equals(id.asString())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean supportsVision(String baseUrl) {
        try {
            HttpResponse<String> response = get(baseUrl + "/props");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }

            JsonNode modalities = jsonMapper.readTree(response.body()).get("modalities");
            JsonNode vision = modalities == null ? null : modalities.get("vision");
            return vision != null && vision.asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String stateFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode status = jsonMapper.readTree(body).get("status");
            return status == null ? null : status.asString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Vision runtime URL is required");
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private long elapsedMs(long started) {
        return Math.max(
                0L,
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
    }
}
