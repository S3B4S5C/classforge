package com.classforge.projectassistant.llm;

import com.classforge.projectassistant.AssistantConversationResponse;
import com.classforge.projectassistant.AssistantMode;
import com.classforge.projectassistant.ProjectAssistantContext;
import com.classforge.projectassistant.ProjectAssistantConversationGateway;
import com.classforge.projectassistant.ProjectAssistantUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "classforge.assistant.text.provider", havingValue = "bedrock")
public class BedrockProjectAssistantGateway implements ProjectAssistantConversationGateway {
    private final BedrockRuntimeClient client;
    private final String model;
    private final int maxTokens;
    private final Duration timeout;
    private final ProjectAssistantConversationSupport support;

    public BedrockProjectAssistantGateway(
            BedrockRuntimeClient client,
            JsonMapper mapper,
            @Value("${classforge.assistant.bedrock.text-model:us.amazon.nova-2-lite-v1:0}") String model,
            @Value("${classforge.assistant.conversation.max-completion-tokens:${classforge.assistant.bedrock.text-max-completion-tokens:2048}}") int maxTokens,
            @Value("${classforge.assistant.conversation.max-context-chars:120000}") int maxContextChars,
            @Value("${classforge.assistant.conversation.request-timeout-millis:90000}") long timeoutMillis
    ) {
        if (timeoutMillis <= 0 || maxTokens <= 0) throw new IllegalArgumentException("Timeout y tokens deben ser positivos.");
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.support = new ProjectAssistantConversationSupport(mapper, maxContextChars);
    }

    @Override
    public AssistantConversationResponse respond(ProjectAssistantContext context, String userMessage, AssistantMode mode) {
        var turns = support.conversation(context, userMessage, mode);
        // Converse providers may require alternating roles. Merge adjacent turns as separate text blocks.
        List<Message> messages = new ArrayList<>();
        for (var turn : turns.subList(1, turns.size())) {
            ConversationRole role = "assistant".equals(turn.role()) ? ConversationRole.ASSISTANT : ConversationRole.USER;
            ContentBlock content = ContentBlock.fromText(turn.content());
            if (!messages.isEmpty() && messages.getLast().role() == role) {
                Message previous = messages.removeLast();
                List<ContentBlock> blocks = new ArrayList<>(previous.content());
                blocks.add(content);
                messages.add(Message.builder().role(role).content(blocks).build());
            } else {
                messages.add(Message.builder().role(role).content(content).build());
            }
        }
        ConverseRequest request = ConverseRequest.builder().modelId(model)
                .system(SystemContentBlock.builder().text(turns.getFirst().content()).build())
                .messages(messages)
                .inferenceConfig(InferenceConfiguration.builder().temperature(0.2f).maxTokens(maxTokens).build())
                .overrideConfiguration(config -> config.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout))
                .build();
        try {
            var response = client.converse(request);
            if (response == null || response.output() == null || response.output().message() == null) {
                throw new ProjectAssistantUnavailableException("Amazon Bedrock devolvió una respuesta vacía.");
            }
            StringBuilder text = new StringBuilder();
            for (var block : response.output().message().content()) {
                if (block.text() != null) text.append(block.text());
            }
            return support.response(text.toString(), context, mode);
        } catch (ProjectAssistantUnavailableException error) {
            throw error;
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException timeoutException) {
            throw new ProjectAssistantUnavailableException("Amazon Bedrock superó el tiempo de espera configurado. Puedes reintentar.", timeoutException);
        } catch (Exception error) {
            throw new ProjectAssistantUnavailableException("No se pudo consultar Amazon Bedrock. Comprueba el proveedor e intenta nuevamente.", error);
        }
    }
}
