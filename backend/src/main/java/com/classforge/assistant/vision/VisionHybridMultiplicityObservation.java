package com.classforge.assistant.vision;

public record VisionHybridMultiplicityObservation(
        String edgeId,
        VisionHybridEndpoint endpoint,
        String rawLabel,
        Double confidence
) {
}
