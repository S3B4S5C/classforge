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

    default VisionHybridMultiplicityAttribution attributeMultiplicity(
            VisionNormalizedImage attributionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel,
            List<String> visibleCompetingEdgeIds
    ) {
        VisionHybridMultiplicityOwnership ownership = verifyMultiplicityOwnership(
                attributionPanel, candidate, endpoint, endpointClass, candidateRawLabel
        );
        String owner = "BELONGS".equals(ownership.ownership()) ? candidate.edgeId()
                : "AMBIGUOUS".equals(ownership.ownership()) ? "AMBIGUOUS" : "NONE";
        return new VisionHybridMultiplicityAttribution(ownership.edgeId(), ownership.endpoint(), owner, ownership.confidence());
    }

    @Deprecated
    default VisionHybridMultiplicityOwnership verifyMultiplicityOwnership(
            VisionNormalizedImage ownershipPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel
    ) {
        throw new UnsupportedOperationException("Multiplicity attribution is required");
    }
}
