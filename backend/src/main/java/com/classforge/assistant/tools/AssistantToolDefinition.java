package com.classforge.assistant.tools;

import java.util.Map;

public record AssistantToolDefinition(
        AssistantToolName name,
        String description,
        Map<String, Object> parameters
) {
    public Map<String, Object> toOpenAiTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name.wireName(),
                        "description", description,
                        "parameters", parameters
                )
        );
    }
}
