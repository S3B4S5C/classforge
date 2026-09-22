package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionHybridProposalAssemblerTests {

    private final VisionHybridProposalAssembler assembler = new VisionHybridProposalAssembler();

    @Test
    void fixedGeometryEndpointsCannotBeChangedAndMarkerDeterminesDirection() {
        VisionUmlProposal semantic = new VisionUmlProposal(
                "dense",
                List.of(
                        klass("c1", "Biblioteca"),
                        klass("c2", "Libro")
                ),
                List.of(), List.of(), 0.9
        );
        VisionGeometryEdgeCandidate candidate = new VisionGeometryEdgeCandidate(
                "E1", "B1", "B2", "c1", "c2", 0.8,
                0, 0, 300, 200, 100, 100, 200, 100
        );
        UmlDiagramGeometry geometry = new UmlDiagramGeometry(
                List.of(), List.of(candidate), new byte[0], new byte[0], new byte[0]
        );
        VisionHybridRelationshipAnnotationProposal annotation = new VisionHybridRelationshipAnnotationProposal(
                List.of(new VisionHybridEdgeAnnotation(
                        "E1", "AGGREGATION", "A",
                        new VisionMultiplicityProposal(1, 1, false),
                        new VisionMultiplicityProposal(0, null, true),
                        "Biblioteca rombo hueco Libro 1 0..*",
                        0.9
                )),
                List.of(), 0.9
        );

        VisionUmlProposal result = assembler.assemble(semantic, geometry, annotation);
        VisionRelationshipProposal relationship = result.safeRelationships().getFirst();
        assertEquals("c1", relationship.sourceRef());
        assertEquals("c2", relationship.targetRef());
        assertEquals("AGGREGATION", relationship.type());
        assertEquals(1, relationship.sourceMultiplicity().lower());
        assertEquals(0, relationship.targetMultiplicity().lower());
        assertEquals(true, relationship.targetMultiplicity().unbounded());

        VisionHybridRelationshipAnnotationProposal inventedEdge = new VisionHybridRelationshipAnnotationProposal(
                List.of(new VisionHybridEdgeAnnotation("E99", "ASSOCIATION", "NONE", null, null, "x", 0.5)),
                List.of(), 0.5
        );
        assertThrows(RuntimeException.class, () -> assembler.assemble(semantic, geometry, inventedEdge));
    }

    private VisionClassProposal klass(String ref, String name) {
        return new VisionClassProposal(ref, name, List.of(), new VisionEvidence(name, 1.0, null, null, null, null));
    }
}
