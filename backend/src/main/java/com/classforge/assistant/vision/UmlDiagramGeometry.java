package com.classforge.assistant.vision;

import java.util.List;

public record UmlDiagramGeometry(
        List<VisionGeometryClassRegion> classRegions,
        List<VisionGeometryEdgeCandidate> edgeCandidates,
        byte[] thresholdPng,
        byte[] segmentsPng,
        byte[] overlayPng
) {
    public List<VisionGeometryClassRegion> safeClassRegions() {
        return classRegions == null ? List.of() : List.copyOf(classRegions);
    }

    public List<VisionGeometryEdgeCandidate> safeEdgeCandidates() {
        return edgeCandidates == null ? List.of() : List.copyOf(edgeCandidates);
    }
}
