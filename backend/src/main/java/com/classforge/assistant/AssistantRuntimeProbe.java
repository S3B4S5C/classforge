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
public class AssistantRuntimeProbe {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(1);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(3);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public AssistantRuntimeProbe(
            JsonMapper jsonMapper
    ) {
        this.jsonMapper =
                jsonMapper;

        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                CONNECT_TIMEOUT
                        )
                        .build();
    }

    public AssistantRuntimeStatus probe(
            String name,
            String baseUrl
    ) {
        long started =
                System.nanoTime();

        try {
            String normalizedBaseUrl =
                    normalizeBaseUrl(
                            baseUrl
                    );

            HttpResponse<String> response =
                    get(
                            normalizedBaseUrl + "/health"
                    );

            long latencyMs =
                    elapsedMs(
                            started
                    );

            String reportedState =
                    stateFromBody(
                            response.body()
                    );

            boolean healthReady =
                    response.statusCode() >= 200
                            && response.statusCode() < 300
                            && "ok".equalsIgnoreCase(
                            reportedState
                    );

            if (!healthReady) {
                String state =
                        response.statusCode() == 503
                                ? "LOADING"
                                : "UNAVAILABLE";

                String message =
                        "HTTP "
                                + response.statusCode()
                                + (
                                reportedState == null
                                        ? ""
                                        : " · " + reportedState
                        );

                return new AssistantRuntimeStatus(
                        name,
                        false,
                        state,
                        latencyMs,
                        message
                );
            }

            if (!matchesExpectedRuntime(
                    name,
                    normalizedBaseUrl,
                    response
            )) {
                return new AssistantRuntimeStatus(
                        name,
                        false,
                        "MISMATCH",
                        latencyMs,
                        "El puerto responde, pero no parece ser " + name
                );
            }

            return new AssistantRuntimeStatus(
                    name,
                    true,
                    "READY",
                    latencyMs,
                    "Listo"
            );
        } catch (Exception exception) {
            return new AssistantRuntimeStatus(
                    name,
                    false,
                    "UNREACHABLE",
                    elapsedMs(
                            started
                    ),
                    "No responde en /health"
            );
        }
    }

    private boolean matchesExpectedRuntime(
            String name,
            String baseUrl,
            HttpResponse<String> healthResponse
    ) {
        if ("llama.cpp".equalsIgnoreCase(name)) {
            return isLlamaCpp(
                    baseUrl
            );
        }

        if ("whisper.cpp".equalsIgnoreCase(name)) {
            return isWhisperCpp(
                    baseUrl,
                    healthResponse
            );
        }

        return true;
    }

    private boolean isLlamaCpp(
            String baseUrl
    ) {
        try {
            HttpResponse<String> response =
                    get(
                            baseUrl + "/v1/models"
                    );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                return false;
            }

            JsonNode root =
                    jsonMapper.readTree(
                            response.body()
                    );

            JsonNode data =
                    root.get(
                            "data"
                    );

            if (
                    data == null
                            || !data.isArray()
            ) {
                return false;
            }

            for (JsonNode model : data) {
                JsonNode ownedBy =
                        model.get(
                                "owned_by"
                        );

                if (
                        ownedBy != null
                                && ownedBy.asString()
                                .toLowerCase(Locale.ROOT)
                                .contains("llama")
                ) {
                    return true;
                }
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isWhisperCpp(
            String baseUrl,
            HttpResponse<String> healthResponse
    ) {
        String serverHeader =
                healthResponse
                        .headers()
                        .firstValue("Server")
                        .orElse("")
                        .toLowerCase(Locale.ROOT);

        if (serverHeader.contains("whisper.cpp")) {
            return true;
        }

        try {
            HttpResponse<String> root =
                    get(
                            baseUrl + "/"
                    );

            return root.statusCode() >= 200
                    && root.statusCode() < 300
                    && root.body() != null
                    && root.body()
                    .toLowerCase(Locale.ROOT)
                    .contains("whisper.cpp");
        } catch (Exception ignored) {
            return false;
        }
    }

    private HttpResponse<String> get(
            String url
    ) throws Exception {
        HttpRequest request =
                HttpRequest
                        .newBuilder(
                                URI.create(
                                        url
                                )
                        )
                        .timeout(
                                REQUEST_TIMEOUT
                        )
                        .GET()
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String stateFromBody(
            String body
    ) {
        if (
                body == null
                        || body.isBlank()
        ) {
            return null;
        }

        try {
            JsonNode root =
                    jsonMapper.readTree(
                            body
                    );

            JsonNode status =
                    root.get(
                            "status"
                    );

            return status == null
                    ? null
                    : status.asString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeBaseUrl(
            String baseUrl
    ) {
        if (
                baseUrl == null
                        || baseUrl.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Runtime URL is required"
            );
        }

        String value =
                baseUrl.trim();

        return value.endsWith("/")
                ? value.substring(
                0,
                value.length() - 1
        )
                : value;
    }

    private long elapsedMs(
            long started
    ) {
        return Math.max(
                0L,
                Duration.ofNanos(
                                System.nanoTime()
                                        - started
                        )
                        .toMillis()
        );
    }
}
