package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.collaboration.protocol.UmlCommandPayload;

import java.util.List;

public record AssistantImagePlanResponse(
        String source,
        String transcript,
        long baseRevision,
        String summary,
        AssistantSemanticPlan plan,
        UmlCommandPayload command,
        List<String> warnings,
        double confidence,
        AssistantImageMetadata image,
        AssistantImagePlanDisposition disposition,
        List<AssistantImageEvidenceItem> evidence
) {
}
