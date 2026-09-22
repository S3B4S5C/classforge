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


    @Test
    void associationClassHybridMergeKeepsUnderlyingRelationshipAndDropsDashedConnectorArtifact() {
        VisionRelationshipProposal semanticUnderlying = new VisionRelationshipProposal(
                "pedido", "producto", "COMPOSITION",
                new VisionMultiplicityProposal(1, null, true),
                new VisionMultiplicityProposal(1, null, true),
                new VisionEvidence("Pedido Producto", 0.9, null, null, null, null)
        );
        VisionUmlProposal semantic = new VisionUmlProposal(
                "association class",
                List.of(
                        klass("pedido", "Pedido"),
                        klass("producto", "Producto"),
                        klass("detalle", "DetallePedido")
                ),
                List.of(semanticUnderlying),
                List.of(new VisionAssociationClassProposal(
                        "detalle", "pedido", "producto",
                        new VisionEvidence("dashed", 0.9, null, null, null, null)
                )),
                List.of(),
                0.9
        );
        UmlDiagramGeometry geometry = new UmlDiagramGeometry(
                List.of(),
                List.of(
                        new VisionGeometryEdgeCandidate(
                                "E1", "B1", "B2", "pedido", "producto", 0.9,
                                0, 0, 300, 100, 100, 50, 200, 50
                        ),
                        new VisionGeometryEdgeCandidate(
                                "E2", "B3", "B1", "detalle", "pedido", 0.7,
                                0, 0, 150, 200, 100, 150, 100, 50
                        )
                ),
                new byte[0], new byte[0], new byte[0]
        );
        VisionHybridRelationshipAnnotationProposal annotation = new VisionHybridRelationshipAnnotationProposal(
                List.of(
                        new VisionHybridEdgeAnnotation(
                                "E1", "COMPOSITION", "A",
                                new VisionMultiplicityProposal(1, null, true),
                                new VisionMultiplicityProposal(1, null, true),
                                "Pedido Producto", 0.9
                        ),
                        new VisionHybridEdgeAnnotation(
                                "E2", "ASSOCIATION", "NONE",
                                null, null, "dashed artifact", 0.5
                        )
                ),
                List.of(), 0.8
        );

        VisionUmlProposal result = assembler.assemble(semantic, geometry, annotation);

        assertEquals(1, result.safeAssociationClasses().size());
        assertEquals(1, result.safeRelationships().size());
        assertEquals("pedido", result.safeRelationships().getFirst().sourceRef());
        assertEquals("producto", result.safeRelationships().getFirst().targetRef());
        assertEquals("COMPOSITION", result.safeRelationships().getFirst().type());
    }

    private VisionClassProposal klass(String ref, String name) {
        return new VisionClassProposal(ref, name, List.of(), new VisionEvidence(name, 1.0, null, null, null, null));
    }
}
