package com.classforge.assistant.vision;

public record VisionHybridMultiplicityAttribution(
        String edgeId,
        VisionHybridEndpoint endpoint,
        String owner,
        Double confidence
) {
}
