package com.classforge.assistant.vision;

import java.util.List;

public interface VisionHybridModelGateway {

    VisionGeometryClassMappingProposal mapClassRegions(
            VisionNormalizedImage labeledRegionsImage,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    );

    VisionHybridRelationshipClassificationProposal classifyRelationship(
            VisionNormalizedImage evidencePanel,
            VisionGeometryEdgeCandidate candidate,
            List<VisionClassProposal> classes
    );

    VisionHybridMultiplicityTranscription transcribeMultiplicity(
            VisionNormalizedImage transcriptionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    );

    VisionHybridMultiplicityAttribution attributeMultiplicity(
            VisionNormalizedImage attributionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel,
            List<String> visibleCompetingEdgeIds
    );
}
