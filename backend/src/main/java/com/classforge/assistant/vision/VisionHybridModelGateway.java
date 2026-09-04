package com.classforge.assistant.vision;

import java.util.List;

public interface VisionHybridModelGateway {

    VisionGeometryClassMappingProposal mapClassRegions(
            VisionNormalizedImage labeledRegionsImage,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    );

    VisionHybridRelationshipAnnotationProposal annotateRelationships(
            VisionNormalizedImage evidenceSheet,
            List<VisionGeometryEdgeCandidate> candidates,
            List<VisionClassProposal> classes
    );
}
