package com.classforge.assistant.vision;

public record VisionGeometryEdgeCandidate(
        String edgeId,
        String aGeometryId,
        String bGeometryId,
        String aClassRef,
        String bClassRef,
        double geometryScore,
        int cropX,
        int cropY,
        int cropWidth,
        int cropHeight,
        int contactAX,
        int contactAY,
        int contactBX,
        int contactBY
) {
}
