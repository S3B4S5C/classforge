package com.classforge.assistant.vision;

public record VisionHybridEdgeClassification(
        String edgeId,
        String type,
        String markerAt,
        String evidenceLabel,
        Double confidence
) {
}
