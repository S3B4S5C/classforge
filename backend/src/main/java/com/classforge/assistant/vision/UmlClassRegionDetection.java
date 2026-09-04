package com.classforge.assistant.vision;

import java.util.List;

public record UmlClassRegionDetection(
        List<VisionGeometryClassRegion> regions,
        byte[] thresholdPng,
        byte[] overlayPng
) {
    public List<VisionGeometryClassRegion> safeRegions() {
        return regions == null ? List.of() : List.copyOf(regions);
    }
}
