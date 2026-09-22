package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionEvidencePresenterTests {

    @Test
    void presentsAssociationClassEvidenceUsingCanonicalClassNames() {
        VisionEvidence evidence = new VisionEvidence(
                "dashed connector to relationship midpoint",
                0.98,
                600,
                290,
                180,
                180
        );
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Pedido con detalle asociativo",
                List.of(
                        new VisionClassProposal("c1", "Pedido", List.of(), null),
                        new VisionClassProposal("c2", "Producto", List.of(), null),
                        new VisionClassProposal("c3", "DetallePedido", List.of(), null)
                ),
                List.of(),
                List.of(new VisionAssociationClassProposal(
                        "c3",
                        "c1",
                        "c2",
                        evidence
                )),
                List.of(),
                0.98
        );

        List<AssistantImageEvidenceItem> items = new VisionEvidencePresenter().items(proposal);

        assertEquals(1, items.size());
        AssistantImageEvidenceItem item = items.getFirst();
        assertEquals("ASSOCIATION_CLASS", item.kind());
        assertEquals("DetallePedido :: Pedido <-> Producto", item.symbol());
        assertEquals(evidence.label(), item.label());
        assertTrue(item.confidence() >= 0.98);
    }
}
