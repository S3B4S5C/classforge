package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionProposalOutcomeNormalizerTests {

    @Test
    void actionableProposalCannotKeepModelGeneratedNoActionableControlWarning() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Cliente visible",
                List.of(new VisionClassProposal(
                        "c1",
                        "Cliente",
                        List.of(),
                        new VisionEvidence("Cliente", null, null, null, null, null)
                )),
                List.of(),
                List.of(
                        "NO_ACTIONABLE_UML: No hay relaciones ni atributos.",
                        "Texto auxiliar poco legible."
                ),
                0.95
        );

        VisionUmlProposal normalized = VisionProposalOutcomeNormalizer.normalize(proposal);

        assertEquals(1, normalized.safeClasses().size());
        assertEquals(List.of("Texto auxiliar poco legible."), normalized.safeWarnings());
        assertFalse(normalized.safeWarnings().stream().anyMatch(
                warning -> warning.startsWith("NO_ACTIONABLE_UML:")
        ));
    }

    @Test
    void emptyProposalGetsCanonicalNoActionableControlWarning() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "No UML",
                List.of(),
                List.of(),
                List.of("Solo hay notas visibles."),
                0.9
        );

        VisionUmlProposal normalized = VisionProposalOutcomeNormalizer.normalize(proposal);

        assertTrue(normalized.safeClasses().isEmpty());
        assertTrue(normalized.safeRelationships().isEmpty());
        assertTrue(normalized.safeWarnings().stream().anyMatch(
                warning -> warning.startsWith("NO_ACTIONABLE_UML:")
        ));
        assertTrue(normalized.safeWarnings().contains("Solo hay notas visibles."));
    }

    @Test
    void canonicalNoActionableWarningIsNotDuplicated() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                null,
                List.of(),
                List.of(),
                List.of("NO_ACTIONABLE_UML: salida antigua del modelo"),
                null
        );

        VisionUmlProposal normalized = VisionProposalOutcomeNormalizer.normalize(proposal);

        assertEquals(1, normalized.safeWarnings().size());
        assertTrue(normalized.safeWarnings().getFirst().startsWith("NO_ACTIONABLE_UML:"));
    }
    @Test
    void associationClassTopologyDropsDashedConnectorArtifactsAndRestoresUnderlyingRelationship() {
        VisionRelationshipProposal underlying = new VisionRelationshipProposal(
                "pedido",
                "producto",
                "COMPOSITION",
                new VisionMultiplicityProposal(1, null, true),
                new VisionMultiplicityProposal(1, null, true),
                new VisionEvidence("Pedido <>-- Producto", 0.95, 1, 1, 100, 30)
        );
        VisionUmlProposal proposal = new VisionUmlProposal(
                "DetallePedido es clase de asociacion",
                List.of(
                        new VisionClassProposal("pedido", "Pedido", List.of(), new VisionEvidence("Pedido", 0.95, 1, 1, 40, 20)),
                        new VisionClassProposal("producto", "Producto", List.of(), new VisionEvidence("Producto", 0.95, 120, 1, 40, 20)),
                        new VisionClassProposal("detalle", "DetallePedido", List.of(), new VisionEvidence("DetallePedido", 0.95, 60, 80, 60, 20))
                ),
                List.of(
                        underlying,
                        new VisionRelationshipProposal(
                                "detalle", "pedido", "ASSOCIATION", null, null,
                                new VisionEvidence("dashed connector", 0.8, 60, 40, 20, 40)
                        )
                ),
                List.of(new VisionAssociationClassProposal(
                        "detalle", "pedido", "producto",
                        new VisionEvidence("DetallePedido dashed to Pedido-Producto", 0.95, 60, 40, 40, 60)
                )),
                List.of(),
                0.95
        );

        VisionUmlProposal normalized = VisionProposalOutcomeNormalizer.normalize(proposal);

        assertEquals(1, normalized.safeAssociationClasses().size());
        assertEquals(1, normalized.safeRelationships().size());
        assertEquals("pedido", normalized.safeRelationships().getFirst().sourceRef());
        assertEquals("producto", normalized.safeRelationships().getFirst().targetRef());
        assertEquals("COMPOSITION", normalized.safeRelationships().getFirst().type());
    }

}
