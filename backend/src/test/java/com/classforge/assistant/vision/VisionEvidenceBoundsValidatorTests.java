package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionEvidenceBoundsValidatorTests {

    private final VisionEvidenceBoundsValidator validator = new VisionEvidenceBoundsValidator();

    @Test
    void acceptsEvidenceInsideNormalizedImage() {
        VisionUmlProposal proposal = proposal(new VisionEvidence("Cliente", 0.9, 10, 20, 100, 50));
        assertDoesNotThrow(() -> validator.validate(proposal, 320, 200));
    }

    @Test
    void rejectsEvidenceOutsideNormalizedImage() {
        VisionUmlProposal proposal = proposal(new VisionEvidence("Cliente", 0.9, 250, 20, 100, 50));
        assertThrows(
                AssistantPlanningException.class,
                () -> validator.validate(proposal, 320, 200)
        );
    }

    @Test
    void validatesAssociationClassEvidenceBounds() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "AssociationClass",
                List.of(
                        new VisionClassProposal("pedido", "Pedido", List.of(), new VisionEvidence("Pedido", 0.9, 10, 10, 80, 30)),
                        new VisionClassProposal("producto", "Producto", List.of(), new VisionEvidence("Producto", 0.9, 200, 10, 80, 30)),
                        new VisionClassProposal("detalle", "DetallePedido", List.of(), new VisionEvidence("DetallePedido", 0.9, 100, 100, 100, 30))
                ),
                List.of(),
                List.of(new VisionAssociationClassProposal(
                        "detalle", "pedido", "producto",
                        new VisionEvidence("dashed connector", 0.9, 310, 20, 20, 20)
                )),
                List.of(),
                0.9
        );

        assertThrows(
                AssistantPlanningException.class,
                () -> validator.validate(proposal, 320, 200)
        );
    }

    private VisionUmlProposal proposal(VisionEvidence evidence) {
        return new VisionUmlProposal(
                "Cliente",
                List.of(new VisionClassProposal("c1", "Cliente", List.of(), evidence)),
                List.of(),
                List.of(),
                0.9
        );
    }
}
