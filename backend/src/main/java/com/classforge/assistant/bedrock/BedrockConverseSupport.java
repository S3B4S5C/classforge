package com.classforge.assistant.bedrock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "classforge.assistant.bedrock.enabled", havingValue = "true")
public class BedrockConverseSupport {

    private final BedrockRuntimeClient client;
    private final JsonMapper jsonMapper;

    public BedrockConverseSupport(BedrockRuntimeClient client, JsonMapper jsonMapper) {
        this.client = client;
        this.jsonMapper = jsonMapper;
    }

    public ToolUseResult invokeToolChoice(
            String modelId,
            String systemPrompt,
            List<Message> messages,
            List<ToolSpec> tools,
            String requiredTool,
            int maxTokens
    ) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("Bedrock requiere al menos una tool.");
        }

        List<Tool> bedrockTools = tools.stream()
                .map(this::tool)
                .toList();
        ToolChoice choice = requiredTool == null || requiredTool.isBlank()
                ? ToolChoice.fromAny(any -> { })
                : ToolChoice.fromTool(tool -> tool.name(requiredTool));

        ConverseRequest request = ConverseRequest.builder()
                .modelId(required(modelId, "Bedrock model"))
                .system(SystemContentBlock.builder().text(required(systemPrompt, "Bedrock system prompt")).build())
                .messages(messages)
                .toolConfig(ToolConfiguration.builder()
                        .tools(bedrockTools)
                        .toolChoice(choice)
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .temperature(0.0f)
                        .maxTokens(Math.max(64, maxTokens))
                        .build())
                .build();

        ConverseResponse response = client.converse(request);
        if (response.output() == null || response.output().message() == null) {
            throw new IllegalStateException("Amazon Bedrock no devolvio un mensaje Converse.");
        }

        for (ContentBlock block : response.output().message().content()) {
            ToolUseBlock toolUse = block.toolUse();
            if (toolUse == null) {
                continue;
            }
            if (requiredTool != null && !requiredTool.isBlank() && !requiredTool.equals(toolUse.name())) {
                continue;
            }
            return new ToolUseResult(
                    toolUse.toolUseId(),
                    toolUse.name(),
                    BedrockDocumentCodec.toJsonNode(jsonMapper, toolUse.input())
            );
        }

        throw new IllegalStateException(
                "Amazon Bedrock no devolvio tool_use"
                        + (requiredTool == null || requiredTool.isBlank() ? "." : " para " + requiredTool + ".")
        );
    }

    public ToolUseResult invokeStructuredImage(
            String modelId,
            String systemPrompt,
            String userPrompt,
            byte[] imageBytes,
            String mediaType,
            String schemaJson,
            String toolName,
            int maxTokens
    ) {
        List<ContentBlock> content = new ArrayList<>();
        content.add(ContentBlock.builder().text(required(userPrompt, "Bedrock user prompt")).build());
        content.add(ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(imageFormat(mediaType))
                        .source(ImageSource.fromBytes(SdkBytes.fromByteArray(imageBytes)))
                        .build())
                .build());

        return invokeToolChoice(
                modelId,
                systemPrompt,
                List.of(Message.builder().role(ConversationRole.USER).content(content).build()),
                List.of(new ToolSpec(
                        toolName,
                        "Devuelve exactamente la estructura solicitada por ClassForge para esta etapa visual.",
                        BedrockDocumentCodec.fromJson(jsonMapper, schemaJson)
                )),
                toolName,
                maxTokens
        );
    }

    public Message userText(String text) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.builder().text(text).build())
                .build();
    }

    public Message assistantToolUse(String id, String name, JsonNode arguments) {
        return Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.builder()
                        .toolUse(ToolUseBlock.builder()
                                .toolUseId(id)
                                .name(name)
                                .input(BedrockDocumentCodec.fromObject(jsonMapper.convertValue(arguments, Object.class)))
                                .build())
                        .build())
                .build();
    }

    public Message userToolResult(String id, String result) {
        ToolResultBlock toolResult = ToolResultBlock.builder()
                .toolUseId(id)
                .content(ToolResultContentBlock.builder().text(result).build())
                .build();
        return Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.builder().toolResult(toolResult).build())
                .build();
    }

    public ToolSpec toolSpec(String name, String description, Map<String, Object> parameters) {
        return new ToolSpec(name, description, BedrockDocumentCodec.fromObject(parameters));
    }

    private Tool tool(ToolSpec spec) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(spec.name())
                        .description(spec.description())
                        .inputSchema(ToolInputSchema.builder().json(novaCompatibleToolSchema(spec.schema())).build())
                        .build())
                .build();
    }

    /**
     * Nova tool schemas accept the regular object schema members but reject metadata/strictness
     * keywords at the root (for example title or additionalProperties). ClassForge keeps its
     * stricter schemas for llama.cpp and validates the returned DTO again after Bedrock, so the
     * Bedrock transport only strips unsupported root keywords; nested contracts are preserved.
     */
    private Document novaCompatibleToolSchema(Document schema) {
        if (schema == null || !schema.isMap()) {
            return schema;
        }
        Map<String, Document> source = schema.asMap();
        Map<String, Document> normalized = new java.util.LinkedHashMap<>();
        for (String key : List.of("type", "properties", "required")) {
            Document value = source.get(key);
            if (value != null) {
                normalized.put(key, value);
            }
        }
        return Document.fromMap(normalized);
    }

    private String imageFormat(String mediaType) {
        if (mediaType == null) {
            return "png";
        }
        return switch (mediaType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    public record ToolSpec(String name, String description, Document schema) {
    }

    public record ToolUseResult(String id, String name, JsonNode input) {
    }
}
