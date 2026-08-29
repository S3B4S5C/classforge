package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlamaNativeToolCallingGateway implements AssistantToolCallingGateway {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    static final Duration CAPABILITY_CACHE = Duration.ofSeconds(60);
    static final int MAX_COMPLETION_TOKENS = 512;

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String url;
    private final String model;

    private volatile Instant capabilityCheckedAt = Instant.EPOCH;
    private volatile boolean nativeToolsSupported;

    public LlamaNativeToolCallingGateway(
            JsonMapper jsonMapper,
            @Value("${classforge.assistant.llama-url:http://127.0.0.1:8092}") String url,
            @Value("${classforge.assistant.llama-model:local-model}") String model
    ) {
        this.jsonMapper = jsonMapper;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public List<AssistantToolInvocation> call(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog
    ) {
        if (catalog.definitions().isEmpty()) {
            throw new AssistantPlanningException("No hay herramientas UML disponibles para esta peticion.");
        }

        ensureNativeToolsSupported();

        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("temperature", 0.0d);
            request.put("max_tokens", MAX_COMPLETION_TOKENS);
            request.put("stream", false);
            request.put("parallel_tool_calls", false);
            request.put("tool_choice", "required");
            request.put(
                    "messages",
                    List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt()
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userText
                            )
                    )
            );
            request.put(
                    "tools",
                    catalog.definitions().stream()
                            .map(AssistantToolDefinition::toOpenAiTool)
                            .toList()
            );

            HttpRequest httpRequest = HttpRequest.newBuilder(
                            URI.create(url + "/v1/chat/completions")
                    )
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AssistantPlanningException(
                        "llama.cpp tool calling respondio HTTP "
                                + response.statusCode()
                                + ": "
                                + abbreviate(response.body(), 500)
                );
            }

            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AssistantPlanningException("llama.cpp no devolvio choices para tool calling.");
            }

            JsonNode message = choices.get(0).get("message");
            JsonNode toolCalls = message == null ? null : message.get("tool_calls");
            if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = message == null || message.get("content") == null
                        ? ""
                        : message.get("content").asString();
                throw new AssistantPlanningException(
                        "Qwen no devolvio tool_calls nativos. Respuesta: " + abbreviate(content, 400)
                );
            }

            List<AssistantToolInvocation> invocations = new ArrayList<>();
            for (JsonNode toolCall : toolCalls) {
                JsonNode function = toolCall.get("function");
                if (function == null || function.get("name") == null || function.get("arguments") == null) {
                    throw new AssistantPlanningException("llama.cpp devolvio un tool_call incompleto.");
                }

                String id = toolCall.get("id") == null
                        ? "tool-" + (invocations.size() + 1)
                        : toolCall.get("id").asString();
                String functionName = function.get("name").asString();
                JsonNode rawArguments = function.get("arguments");
                JsonNode arguments = rawArguments.isTextual()
                        ? jsonMapper.readTree(rawArguments.asString())
                        : rawArguments;

                invocations.add(
                        new AssistantToolInvocation(
                                id,
                                AssistantToolName.fromWireName(functionName),
                                arguments
                        )
                );
            }

            return List.copyOf(invocations);
        } catch (HttpTimeoutException exception) {
            throw new AssistantPlanningException(
                    "El modelo local supero "
                            + REQUEST_TIMEOUT.toSeconds()
                            + " segundos preparando tool calls.",
                    exception
            );
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException(
                    "No pudimos ejecutar native tool calling contra llama.cpp en " + url,
                    exception
            );
        }
    }

    private synchronized void ensureNativeToolsSupported() {
        Instant now = Instant.now();
        if (Duration.between(capabilityCheckedAt, now).compareTo(CAPABILITY_CACHE) < 0) {
            if (!nativeToolsSupported) {
                throw unsupportedTools();
            }
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/props"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            boolean supported = false;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = jsonMapper.readTree(response.body());
                JsonNode caps = root.get("chat_template_caps");
                supported = caps != null
                        && bool(caps.get("supports_tools"))
                        && bool(caps.get("supports_tool_calls"));
            }

            nativeToolsSupported = supported;
            capabilityCheckedAt = now;

            if (!supported) {
                throw unsupportedTools();
            }
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            capabilityCheckedAt = now;
            nativeToolsSupported = false;
            throw new AssistantPlanningException(
                    "No pudimos comprobar las capacidades de tool calling de llama.cpp en " + url,
                    exception
            );
        }
    }

    private AssistantPlanningException unsupportedTools() {
        return new AssistantPlanningException(
                "llama.cpp no reporta supports_tools/supports_tool_calls. "
                        + "Para planner=tools inicia un modelo tool-aware como Qwen2.5-Instruct con --jinja."
        );
    }

    private boolean bool(JsonNode node) {
        return node != null && "true".equalsIgnoreCase(node.asString());
    }

    private String systemPrompt() {
        return """
                Eres el planificador UML local de ClassForge.
                Debes responder exclusivamente mediante una o mas tool_calls de las herramientas proporcionadas.
                No escribas una respuesta conversacional.

                REGLAS:
                - El proyecto actual esta representado por enums dinamicos dentro de las tools.
                - Para elementos existentes selecciona exactamente un valor canonico de esos enums.
                - No inventes clases, atributos ni relaciones existentes.
                - Los nombres nuevos son texto libre: preserva lo que pidio el usuario, incluso si parece un typo.
                - No conviertas el estado actual del proyecto en acciones no solicitadas.
                - Usa la herramienta semanticamente correcta para la peticion.
                - Para renombrar atributos usa rename_attribute y devuelve new_name sin prefijo de clase.
                - Para multiplicidad usa set_relationship_multiplicity y end_class debe ser el extremo cuya multiplicidad cambia.
                - Para herencia usa create_generalization: subclass hereda de superclass.
                - Para agregacion/composicion: whole_class es el todo y part_class es la parte.
                - Para upper infinito usa -1; lower nunca puede ser negativo.
                - "muchas/muchos" sin minimo explicito significa 0..*; "cero o muchas" significa 0..*.
                - Si una propiedad opcional no fue solicitada, omitela.
                - En este primer incremento evita depender de una clase creada por otra tool_call de la misma respuesta.
                """;
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
