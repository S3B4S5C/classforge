package com.classforge.assistant.vision;

import java.util.List;

public record VisionHybridRelationshipAnnotationProposal(
        List<VisionHybridEdgeAnnotation> edges,
        List<String> warnings,
        Double confidence
) {
    public List<VisionHybridEdgeAnnotation> safeEdges() {
        return edges == null ? List.of() : List.copyOf(edges);
    }

    public List<String> safeWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
