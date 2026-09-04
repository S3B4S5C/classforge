package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantSemanticPlan;

import java.util.List;

public record VisionCompilationResult(
        AssistantSemanticPlan plan,
        List<String> warnings,
        double confidence
) {
}
