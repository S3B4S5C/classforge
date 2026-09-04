package com.classforge.assistant.vision;

public record AssistantImageMetadata(
        String filename,
        String originalMediaType,
        String normalizedMediaType,
        int width,
        int height,
        long originalBytes,
        String sha256,
        boolean reencoded
) {
}
