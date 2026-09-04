package com.classforge.assistant.vision;

public record VisionGeometryClassRegion(
        String geometryId,
        String classRef,
        int x,
        int y,
        int width,
        int height,
        Double confidence
) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }
}
