package com.classforge.assistant.vision;

public record VisionHybridEdgeAnnotation(
        String edgeId,
        String type,
        String markerAt,
        VisionMultiplicityProposal multiplicityA,
        VisionMultiplicityProposal multiplicityB,
        String evidenceLabel,
        Double confidence
) {
}
