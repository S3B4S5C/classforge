package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionProposalGroundingValidatorTests {

    private final VisionProposalGroundingValidator validator =
            new VisionProposalGroundingValidator();

    @Test
    void acceptsEvidenceThatNamesDetectedSymbols() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Cliente y Factura",
                List.of(
                        new VisionClassProposal(
                                "c1",
                                "Cliente",
                                List.of(
                                        new VisionAttributeProposal(
                                                "email", "STRING", null, "PRIVATE", true, false,
                                                evidence("email : String")
                                        )
                                ),
                                evidence("Cliente")
                        ),
                        new VisionClassProposal("c2", "Factura", List.of(), evidence("Factura"))
                ),
                List.of(
                        new VisionRelationshipProposal(
                                "c1", "c2", "ASSOCIATION", null, null,
                                evidence("Cliente -- Factura")
                        )
                ),
                List.of(),
                0.93
        );

        assertDoesNotThrow(() -> validator.validate(proposal));
    }

    @Test
    void acceptsAttributeWhenExactSymbolExistsInEnclosingClassEvidence() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Libro manuscrito",
                List.of(
                        new VisionClassProposal(
                                "c1",
                                "Libro",
                                List.of(
                                        new VisionAttributeProposal(
                                                "titulo", null, null, "PRIVATE", null, null,
                                                evidence("Libro\titulo")
                                        )
                                ),
                                evidence("Libro\nid\nisbn\ntitulo\nañoPublicacion")
                        )
                ),
                List.of(),
                List.of(),
                0.85
        );

        assertDoesNotThrow(() -> validator.validate(proposal));
    }

    @Test
    void failsClosedWhenNeitherAttributeNorClassEvidenceBacksTheSymbol() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Bad attribute evidence",
                List.of(
                        new VisionClassProposal(
                                "c1",
                                "Libro",
                                List.of(
                                        new VisionAttributeProposal(
                                                "titulo", null, null, "PRIVATE", null, null,
                                                evidence("Libro")
                                        )
                                ),
                                evidence("Libro\nid\nisbn")
                        )
                ),
                List.of(),
                List.of(),
                0.8
        );

        assertThrows(AssistantPlanningException.class, () -> validator.validate(proposal));
    }

    @Test
    void failsClosedWhenEvidenceDoesNotBackTheSymbol() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Bad",
                List.of(
                        new VisionClassProposal(
                                "c1", "Cliente", List.of(), evidence("Factura")
                        )
                ),
                List.of(),
                List.of(),
                0.8
        );

        assertThrows(AssistantPlanningException.class, () -> validator.validate(proposal));
    }

    @Test
    void rejectsNoActionableWarningWhenProposalContainsUml() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Contradictory",
                List.of(new VisionClassProposal(
                        "c1", "Cliente", List.of(), evidence("Cliente")
                )),
                List.of(),
                List.of("NO_ACTIONABLE_UML: no hay UML"),
                0.8
        );

        assertThrows(AssistantPlanningException.class, () -> validator.validate(proposal));
    }

    @Test
    void acceptsNoActionableWarningForEmptyProposal() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "No UML",
                List.of(),
                List.of(),
                List.of("NO_ACTIONABLE_UML: solo notas"),
                0.9
        );

        assertDoesNotThrow(() -> validator.validate(proposal));
    }

    @Test
    void rejectsPartialBoundingBoxes() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Partial bbox",
                List.of(
                        new VisionClassProposal(
                                "c1",
                                "Cliente",
                                List.of(),
                                new VisionEvidence("Cliente", 0.9, 10, null, 100, null)
                        )
                ),
                List.of(),
                List.of(),
                0.9
        );

        assertThrows(AssistantPlanningException.class, () -> validator.validate(proposal));
    }

    private VisionEvidence evidence(String label) {
        return new VisionEvidence(label, 0.9, 1, 1, 100, 40);
    }
}
