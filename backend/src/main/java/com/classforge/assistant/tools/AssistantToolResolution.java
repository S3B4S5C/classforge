package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantSemanticPlan;

import java.util.List;

public record AssistantToolResolution(
        AssistantSemanticPlan plan,
        List<AssistantResolvedReference> references
) {
}
