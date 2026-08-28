package com.classforge.assistant;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
            HttpRequest request =
                    HttpRequest
                            .newBuilder(
                                    URI.create(
                                            normalizeBaseUrl(
                                                    baseUrl
                                            )
                                                    + "/health"
                                    )
                            )
                            .timeout(
                                    REQUEST_TIMEOUT
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            long latencyMs =
                    elapsedMs(
                            started
                    );

            String reportedState =
                    stateFromBody(
                            response.body()
                    );

            boolean available =
                    response.statusCode() >= 200
                            && response.statusCode() < 300
                            && "ok".equalsIgnoreCase(
                            reportedState
                    );

            String state =
                    available
                            ? "READY"
                            : (
                            response.statusCode() == 503
                                    ? "LOADING"
                                    : "UNAVAILABLE"
                    );

            String message =
                    available
                            ? "Listo"
                            : "HTTP "
                            + response.statusCode()
                            + (
                            reportedState == null
                                    ? ""
                                    : " · " + reportedState
                    );

            return new AssistantRuntimeStatus(
                    name,
                    available,
                    state,
                    latencyMs,
                    message
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