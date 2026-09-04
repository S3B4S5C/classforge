package com.classforge.assistant.vision;

public record AssistantImageEvidenceItem(
        String kind,
        String symbol,
        String label,
        Double confidence,
        Integer x,
        Integer y,
        Integer width,
        Integer height
) {
}
