package com.classforge.assistant.vision;

public record VisionNormalizedImage(
        String originalFilename,
        String originalMediaType,
        String mediaType,
        byte[] bytes,
        int width,
        int height,
        String sha256,
        boolean reencoded
) {
}
