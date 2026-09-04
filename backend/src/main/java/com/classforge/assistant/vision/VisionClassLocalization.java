package com.classforge.assistant.vision;

public record VisionClassLocalization(
        String classRef,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Double confidence
) {
}
