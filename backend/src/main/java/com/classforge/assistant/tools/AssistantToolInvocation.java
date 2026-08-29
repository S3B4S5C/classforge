package com.classforge.assistant.tools;

import tools.jackson.databind.JsonNode;

public record AssistantToolInvocation(
        String id,
        AssistantToolName name,
        JsonNode arguments
) {
}
