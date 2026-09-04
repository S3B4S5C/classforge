package com.classforge.assistant.vision;

public record VisionEvidence(
        String label,
        Double confidence,
        Integer x,
        Integer y,
        Integer width,
        Integer height
) {
}
