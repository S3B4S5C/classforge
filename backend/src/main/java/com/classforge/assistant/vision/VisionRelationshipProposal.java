package com.classforge.assistant.vision;

public record VisionRelationshipProposal(
        String sourceRef,
        String targetRef,
        String type,
        VisionMultiplicityProposal sourceMultiplicity,
        VisionMultiplicityProposal targetMultiplicity,
        VisionEvidence evidence
) {
}
