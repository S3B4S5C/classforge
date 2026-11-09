package com.classforge.projectassistant.llm;

import com.classforge.assistant.groq.GroqClient;
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

import java.util.Map;

@Component
@ConditionalOnProperty(name = "classforge.assistant.text.provider", havingValue = "groq")
public class GroqProjectAssistantGateway implements ProjectAssistantConversationGateway {
    private final GroqClient client;
    private final String model;
    private final int maxTokens;
    private final ProjectAssistantConversationSupport support;

    public GroqProjectAssistantGateway(
            GroqClient client,
            JsonMapper mapper,
            @Value("${classforge.assistant.groq.text-model:openai/gpt-oss-120b}") String model,
            @Value("${classforge.assistant.conversation.max-completion-tokens:2048}") int maxTokens,
            @Value("${classforge.assistant.conversation.max-context-chars:18000}") int maxContextChars
    ) {
        if (model == null || model.isBlank() || maxTokens <= 0) throw new IllegalArgumentException("Modelo Groq y tokens deben ser válidos.");
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.support = new ProjectAssistantConversationSupport(mapper, maxContextChars);
    }

    @Override
    public AssistantConversationResponse respond(ProjectAssistantContext context, String userMessage, AssistantMode mode) {
        var conversation = support.conversation(context, userMessage, mode);
        Map<String, Object> payload = Map.of("model", model, "temperature", 0.2d, "stream", false,
                "max_completion_tokens", maxTokens, "messages", conversation);
        // Propagate GroqApiException unchanged so HTTP handlers can retain 429 and Retry-After.
        JsonNode envelope = client.chat(payload);
        JsonNode choices = envelope == null ? null : envelope.get("choices");
        JsonNode choice = choices != null && choices.isArray() && !choices.isEmpty() ? choices.get(0) : null;
        JsonNode finish = choice == null ? null : choice.get("finish_reason");
        if (finish != null && finish.isString() && "length".equals(finish.asString())) {
            throw new ProjectAssistantUnavailableException("Groq devolvió una respuesta incompleta por límite de tokens. Puedes reintentar con una consulta más breve.");
        }
        JsonNode message = choice == null ? null : choice.get("message");
        JsonNode content = message == null ? null : message.get("content");
        return support.response(content != null && content.isString() ? content.asString() : null, context, mode);
    }
}
