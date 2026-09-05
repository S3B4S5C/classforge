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
        int contactBY,
        Integer innerAX,
        Integer innerAY,
        Integer innerBX,
        Integer innerBY
) {
    public VisionGeometryEdgeCandidate(
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
        this(
                edgeId, aGeometryId, bGeometryId, aClassRef, bClassRef, geometryScore,
                cropX, cropY, cropWidth, cropHeight, contactAX, contactAY, contactBX, contactBY,
                null, null, null, null
        );
    }
}
