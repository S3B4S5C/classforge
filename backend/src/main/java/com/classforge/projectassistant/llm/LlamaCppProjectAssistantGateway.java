package com.classforge.projectassistant.llm;

import com.classforge.projectassistant.AssistantConversationResponse;
import com.classforge.projectassistant.AssistantMode;
import com.classforge.projectassistant.ProjectAssistantContext;
import com.classforge.projectassistant.ProjectAssistantConversationGateway;
import com.classforge.projectassistant.ProjectAssistantUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "classforge.assistant.text.provider", havingValue = "llama-cpp", matchIfMissing = true)
public class LlamaCppProjectAssistantGateway implements ProjectAssistantConversationGateway {
    private final JsonMapper mapper;
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;
    private final ProjectAssistantConversationSupport support;

    public LlamaCppProjectAssistantGateway(
            JsonMapper mapper,
            @Value("${classforge.assistant.llama-url:http://127.0.0.1:8092}") String url,
            @Value("${classforge.assistant.llama-model:local-model}") String model,
            @Value("${classforge.assistant.conversation.request-timeout-millis:90000}") long timeoutMillis,
            @Value("${classforge.assistant.conversation.max-context-chars:120000}") int maxContextChars,
            @Value("${classforge.assistant.conversation.max-completion-tokens:2048}") int maxTokens
    ) {
        if (timeoutMillis <= 0 || maxTokens <= 0) throw new IllegalArgumentException("Timeout y tokens deben ser positivos.");
        this.mapper = mapper;
        this.endpoint = URI.create(url.replaceAll("/+$", "") + "/v1/chat/completions");
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.maxTokens = maxTokens;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(3000, timeoutMillis))).build();
        this.support = new ProjectAssistantConversationSupport(mapper, maxContextChars);
    }

    @Override
    public AssistantConversationResponse respond(ProjectAssistantContext context, String userMessage, AssistantMode mode) {
        var conversation = support.conversation(context, userMessage, mode);
        var payload = Map.of("model", model, "temperature", 0.2d, "stream", false, "max_tokens", maxTokens,
                "messages", conversation);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProjectAssistantUnavailableException("llama.cpp respondió HTTP " + response.statusCode() + ". Intenta nuevamente.");
            }
            JsonNode root;
            try {
                root = mapper.readTree(response.body());
            } catch (Exception invalidJson) {
                throw new ProjectAssistantUnavailableException("llama.cpp devolvió un JSON de respuesta inválido.", invalidJson);
            }
            JsonNode choices = root == null ? null : root.get("choices");
            JsonNode choice = choices != null && choices.isArray() && !choices.isEmpty() ? choices.get(0) : null;
            JsonNode message = choice == null ? null : choice.get("message");
            JsonNode content = message == null ? null : message.get("content");
            return support.response(content != null && content.isString() ? content.asString() : null, context, mode);
        } catch (HttpTimeoutException timeoutException) {
            throw new ProjectAssistantUnavailableException("llama.cpp superó el tiempo de espera configurado. Puedes reintentar.", timeoutException);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProjectAssistantUnavailableException("Se interrumpió la consulta al asistente.", interrupted);
        } catch (IOException unavailable) {
            throw new ProjectAssistantUnavailableException("No se pudo conectar con llama.cpp. Comprueba el proveedor e intenta nuevamente.", unavailable);
        }
    }
}
