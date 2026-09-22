package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "classforge.assistant.text.provider", havingValue = "llama-cpp", matchIfMissing = true)
public class LlamaNativeToolCallingGateway implements AssistantToolCallingGateway {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    static final Duration CAPABILITY_CACHE = Duration.ofSeconds(60);
    static final int MAX_COMPLETION_TOKENS = 256;
    static final int RETRY_COMPLETION_TOKENS = 512;

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
            AssistantToolCatalog catalog,
            List<AssistantToolConversationTurn> history
    ) {
        return callInternal(userText, document, catalog, history, false, null);
    }

    private List<AssistantToolInvocation> callInternal(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog,
            List<AssistantToolConversationTurn> history,
            boolean retryAttempt,
            String retryInstruction
    ) {
        if (catalog.definitions().isEmpty()) {
            throw new AssistantPlanningException("No hay herramientas UML disponibles para esta peticion.");
        }

        ensureNativeToolsSupported();

        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("temperature", 0.0d);
            request.put("max_tokens", retryAttempt ? RETRY_COMPLETION_TOKENS : MAX_COMPLETION_TOKENS);
            request.put("stream", false);
            request.put("parallel_tool_calls", false);
            request.put("tool_choice", "required");
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt(catalog)));
            String effectiveUserText = retryInstruction == null
                    ? userText
                    : userText + "\n\nIMPORTANTE: " + retryInstruction;
            messages.add(Map.of("role", "user", "content", effectiveUserText));
            if (history != null) {
                for (AssistantToolConversationTurn turn : history) {
                    List<Map<String, Object>> priorCalls = new ArrayList<>();
                    for (AssistantToolInvocation invocation : turn.invocations()) {
                        priorCalls.add(Map.of(
                                "id", invocation.id(),
                                "type", "function",
                                "function", Map.of(
                                        "name", invocation.name().wireName(),
                                        "arguments", jsonMapper.writeValueAsString(invocation.arguments())
                                )
                        ));
                    }
                    messages.add(Map.of(
                            "role", "assistant",
                            "content", "",
                            "tool_calls", priorCalls
                    ));
                    for (int i = 0; i < turn.invocations().size(); i++) {
                        messages.add(Map.of(
                                "role", "tool",
                                "tool_call_id", turn.invocations().get(i).id(),
                                "content", turn.results().get(i)
                        ));
                    }
                }
            }
            request.put("messages", messages);
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

            JsonNode root;
            try {
                root = jsonMapper.readTree(response.body());
            } catch (Exception malformedEnvelope) {
                if (!retryAttempt && catalog.definitions().size() == 1) {
                    return callInternal(
                            userText, document, catalog, history, true,
                            "la respuesta JSON anterior de llama.cpp quedo truncada. Devuelve exactamente una "
                                    + "tool_call completa, sin texto ni llamadas repetidas."
                    );
                }
                throw new AssistantPlanningException(
                        "llama.cpp devolvio una respuesta JSON truncada incluso despues del reintento: "
                                + abbreviate(response.body(), 300),
                        malformedEnvelope
                );
            }
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
                if (!retryAttempt && catalog.definitions().size() == 1) {
                    return callInternal(
                            userText, document, catalog, history, true,
                            "responde con exactamente una tool_call completa de la unica herramienta expuesta. "
                                    + "No escribas texto conversacional ni repitas la llamada."
                    );
                }
                throw new AssistantPlanningException(
                        "Qwen no devolvio tool_calls nativos despues del reintento obligatorio. Respuesta: "
                                + abbreviate(content, 400)
                );
            }

            List<AssistantToolName> exposedTools = catalog.definitions().stream()
                    .map(AssistantToolDefinition::name)
                    .toList();

            // llama.cpp/Qwen can occasionally emit a burst of repeated tool_calls
            // even with parallel_tool_calls=false. We only need the first call that
            // targets a tool actually exposed by the current (normally single-tool)
            // catalog. Parsing every trailing call is unsafe because max_tokens may
            // truncate the tail in the middle of its JSON arguments even though the
            // first call is already complete and valid.
            for (JsonNode toolCall : toolCalls) {
                JsonNode function = toolCall.get("function");
                if (function == null || function.get("name") == null || function.get("arguments") == null) {
                    continue;
                }

                AssistantToolName toolName;
                try {
                    toolName = AssistantToolName.fromWireName(function.get("name").asString());
                } catch (RuntimeException exception) {
                    continue;
                }
                if (!exposedTools.contains(toolName)) {
                    continue;
                }

                String id = toolCall.get("id") == null ? "tool-1" : toolCall.get("id").asString();
                JsonNode rawArguments = function.get("arguments");
                JsonNode arguments;
                try {
                    arguments = rawArguments.isTextual()
                            ? jsonMapper.readTree(rawArguments.asString())
                            : rawArguments;
                } catch (Exception malformedArguments) {
                    if (!retryAttempt && catalog.definitions().size() == 1) {
                        return callInternal(
                                userText, document, catalog, history, true,
                                "la tool_call anterior quedo truncada. Devuelve exactamente una sola tool_call, "
                                        + "con JSON de argumentos completo y sin repetir llamadas."
                        );
                    }
                    throw new AssistantPlanningException(
                            "Qwen devolvio argumentos JSON truncados para " + toolName.wireName()
                                    + " incluso despues del reintento: "
                                    + abbreviate(rawArguments.asString(), 300),
                            malformedArguments
                    );
                }

                return List.of(new AssistantToolInvocation(id, toolName, arguments));
            }

            if (!retryAttempt && catalog.definitions().size() == 1) {
                return callInternal(
                        userText, document, catalog, history, true,
                        "usa exactamente la unica herramienta expuesta y devuelve una sola tool_call completa."
                );
            }
            throw new AssistantPlanningException(
                    "Qwen devolvio tool_calls, pero ninguna corresponde a las herramientas expuestas: "
                            + exposedTools.stream().map(AssistantToolName::wireName).toList()
            );
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
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getClass().getSimpleName() + ": " + abbreviate(exception.getMessage(), 240);
            throw new AssistantPlanningException(
                    "No pudimos ejecutar native tool calling contra llama.cpp en " + url + " (" + detail + ")",
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

    private String systemPrompt(AssistantToolCatalog catalog) {
        if (catalog.definitions().size() == 1
                && catalog.definitions().getFirst().name() == AssistantToolName.ROUTE_REQUEST) {
            return routingSystemPrompt();
        }
        return """
                Eres el planificador UML local de ClassForge.
                Debes responder exclusivamente mediante exactamente una tool_call de la herramienta proporcionada para este paso.
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
                - ClassForge ya enruto el paso actual: emite exactamente una llamada a la unica tool UML expuesta para ese paso.
                - En peticiones compuestas ClassForge te invoca de nuevo con el siguiente paso y un ProjectDocument efimero actualizado.
                - Si una clase fue creada en una ronda previa, ya aparece en el catalogo actual y puede usarse como referencia existente.
                """;
    }


    private String routingSystemPrompt() {
        return """
                Eres el router de operaciones UML de ClassForge.
                Responde exclusivamente con exactamente una llamada a route_uml_request.

                Debes convertir la peticion en una lista ORDENADA de herramientas semanticas.
                No resuelvas nombres de clases ni atributos y no inventes cambios: solo elige operaciones.
                Usa un unico step para una peticion simple y varios steps solo si el usuario pide varios cambios.
                Si una clase nueva se usa despues, create_class debe ir antes de add_attributes o relaciones que la referencien.

                Ejemplos de significado:
                - crear clase -> create_class
                - renombrar clase -> rename_class
                - borrar clase -> delete_class
                - agregar campo/atributo -> add_attributes
                - renombrar atributo -> rename_attribute
                - cambiar propiedades de atributo -> update_attribute_properties
                - borrar atributo -> delete_attribute
                - asociar/conectar -> create_association
                - agregacion -> create_aggregation
                - composicion/contiene como parte fuerte -> create_composition
                - herencia/especializacion -> create_generalization
                - cambiar 0..*, 1..*, muchas, ninguna o varias -> set_relationship_multiplicity
                - cambiar tipo de relacion -> change_relationship_type
                - desconectar/quitar vinculo/relacion existente -> delete_relationship

                Para "crea Cliente, agregale email y relacionala con Factura" devuelve:
                [create_class, add_attributes, create_association].
                """;
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
