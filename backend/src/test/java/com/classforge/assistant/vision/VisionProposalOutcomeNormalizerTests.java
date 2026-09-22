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
}
