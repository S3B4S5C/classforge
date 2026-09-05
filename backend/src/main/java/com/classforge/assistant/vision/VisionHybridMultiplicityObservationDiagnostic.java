package com.classforge.assistant.vision;

public record VisionHybridMultiplicityObservationDiagnostic(
        String edgeId,
        VisionHybridEndpoint endpoint,
        String classRef,
        String rawLabel,
        Double transcriptionConfidence,
        String rawOwner,
        Double attributionConfidence,
        java.util.List<String> visibleCompetingEdgeIds,
        String effectiveOwner,
        String rejectionReason,
        VisionMultiplicityProposal parsedMultiplicity,
        boolean finalAccepted
) {
}
