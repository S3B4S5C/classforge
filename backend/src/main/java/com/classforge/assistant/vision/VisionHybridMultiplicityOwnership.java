package com.classforge.assistant.vision;

public record VisionHybridMultiplicityOwnership(
        String edgeId,
        VisionHybridEndpoint endpoint,
        String ownership,
        Double confidence
) {
}
