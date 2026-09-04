package com.classforge.assistant.vision;

public record VisionHybridDiagnostics(
        UmlClassRegionDetection classRegionDetection,
        VisionGeometryClassMappingProposal mapping,
        UmlDiagramGeometry geometry,
        VisionNormalizedImage relationshipEvidenceSheet,
        VisionHybridRelationshipAnnotationProposal annotation
) {
}
