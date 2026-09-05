package com.classforge.assistant.vision;

import java.util.List;

public record VisionHybridRelationshipClassificationProposal(
        List<VisionHybridEdgeClassification> edges,
        List<String> warnings,
        Double confidence
) {
    public List<VisionHybridEdgeClassification> safeEdges() {
        return edges == null ? List.of() : List.copyOf(edges);
    }

    public List<String> safeWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
