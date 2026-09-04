package com.classforge.assistant.vision;

public record VisionImageInput(
        String originalFilename,
        String mediaType,
        byte[] bytes,
        int width,
        int height,
        String sha256
) {
}
