package com.classforge.assistant.vision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "classforge.assistant.vision.provider",
        havingValue = "unconfigured",
        matchIfMissing = true
)
public class UnconfiguredVisionHybridModelGateway implements VisionHybridModelGateway {

    private VisionModelGatewayException unavailable() {
        return new VisionModelGatewayException(
                VisionModelGatewayException.Reason.TRANSPORT,
                "El runtime visual hibrido no esta configurado.",
                null
        );
    }

    @Override
    public VisionGeometryClassMappingProposal mapClassRegions(
            VisionNormalizedImage labeledRegionsImage,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    ) {
        throw unavailable();
    }

    @Override
    public VisionHybridRelationshipClassificationProposal classifyRelationship(
            VisionNormalizedImage evidencePanel,
            VisionGeometryEdgeCandidate candidate,
            List<VisionClassProposal> classes
    ) {
        throw unavailable();
    }

    @Override
    public VisionHybridMultiplicityTranscription transcribeMultiplicity(
            VisionNormalizedImage transcriptionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    ) {
        throw unavailable();
    }

    @Override
    public VisionHybridMultiplicityAttribution attributeMultiplicity(
            VisionNormalizedImage attributionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel,
            List<String> visibleCompetingEdgeIds
    ) {
        throw unavailable();
    }
}
