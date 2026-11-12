package com.classforge.assistant.vision;

public record VisionRelationshipProposal(
        String sourceRef,
        String targetRef,
        String type,
        VisionMultiplicityProposal sourceMultiplicity,
        VisionMultiplicityProposal targetMultiplicity,
        VisionEvidence evidence,
        String associationClassRef
) {
    public VisionRelationshipProposal(
            String sourceRef, String targetRef, String type,
            VisionMultiplicityProposal sourceMultiplicity,
            VisionMultiplicityProposal targetMultiplicity,
            VisionEvidence evidence
    ) {
        this(sourceRef, targetRef, type, sourceMultiplicity, targetMultiplicity, evidence, null);
    }
}
