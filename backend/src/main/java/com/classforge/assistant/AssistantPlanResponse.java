package com.classforge.assistant;

import com.classforge.collaboration.protocol.UmlCommandPayload;

public record AssistantPlanResponse(
        String source,
        String transcript,
        long baseRevision,
        String summary,
        AssistantSemanticPlan plan,
        UmlCommandPayload command
) {
}