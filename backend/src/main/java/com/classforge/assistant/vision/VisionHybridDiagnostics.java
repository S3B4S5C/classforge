package com.classforge.assistant.vision;

import java.util.List;

public record VisionHybridDiagnostics(
        UmlClassRegionDetection classRegionDetection,
        VisionGeometryClassMappingProposal mapping,
        UmlDiagramGeometry geometry,
        VisionNormalizedImage relationshipEvidenceSheet,
        VisionHybridRelationshipAnnotationProposal annotation,
        List<VisionNormalizedImage> relationshipPanels,
        List<VisionNormalizedImage> multiplicityEndpointPanels,
        List<VisionHybridMultiplicityObservationDiagnostic> multiplicityObservations
) {
    public List<VisionNormalizedImage> safeRelationshipPanels() {
        return relationshipPanels == null ? List.of() : List.copyOf(relationshipPanels);
    }

    public List<VisionNormalizedImage> safeMultiplicityEndpointPanels() {
        return multiplicityEndpointPanels == null ? List.of() : List.copyOf(multiplicityEndpointPanels);
    }

    public List<VisionHybridMultiplicityObservationDiagnostic> safeMultiplicityObservations() {
        return multiplicityObservations == null ? List.of() : List.copyOf(multiplicityObservations);
    }
}
