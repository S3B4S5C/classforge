package com.classforge.assistant.vision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(LlamaCppVisionHybridModelGateway.class)
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
    public VisionHybridRelationshipAnnotationProposal annotateRelationships(
            VisionNormalizedImage evidenceSheet,
            List<VisionGeometryEdgeCandidate> candidates,
            List<VisionClassProposal> classes
    ) {
        throw unavailable();
    }
}
