package com.classforge.assistant.vision;

public record VisionHybridMultiplicityTranscription(
        String edgeId,
        VisionHybridEndpoint endpoint,
        String rawLabel,
        Double confidence
) {
}
