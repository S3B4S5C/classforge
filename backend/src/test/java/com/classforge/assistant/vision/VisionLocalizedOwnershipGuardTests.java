package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VisionLocalizedOwnershipGuardTests {

    @Test
    void convertsNormalizedCoordinatesAcrossEntireOwnershipPanel() {
        assertEquals(new VisionOwnershipPanelPoint(0.0, 0.0),
                VisionLocalizedOwnershipGuard.toPanelPoint(0, 0, 256, 280));
        assertEquals(new VisionOwnershipPanelPoint(255.0, 279.0),
                VisionLocalizedOwnershipGuard.toPanelPoint(1000, 1000, 256, 280));
        VisionOwnershipPanelPoint center = VisionLocalizedOwnershipGuard.toPanelPoint(500, 500, 256, 280);
        assertEquals(127.5, center.x(), 0.001);
        assertEquals(139.5, center.y(), 0.001);
    }

    @Test
    void preservesBelongsWhenLabelIsClearlyCloserToCurrentConnector() {
        VisionLocalizedOwnershipGuard.Evaluation result = evaluate(10, 5, List.of(guide("E2", 0, 30)));

        assertEquals("BELONGS", result.effectiveOwnership());
        assertEquals(5.0, result.currentConnectorDistance(), 0.001);
        assertEquals(25.0, result.nearestCompetingDistance(), 0.001);
        assertNull(result.deterministicOwnershipReason());
    }

    @Test
    void vetoesBelongsWhenCompetitorClearlyDominates() {
        VisionLocalizedOwnershipGuard.Evaluation result = evaluate(10, 30, List.of(guide("E2", 0, 25)));

        assertEquals("NOT_BELONGS", result.effectiveOwnership());
        assertEquals("E2", result.nearestCompetingEdgeId());
        assertEquals(30.0, result.currentConnectorDistance(), 0.001);
        assertEquals(5.0, result.nearestCompetingDistance(), 0.001);
        assertEquals(8.01, result.dominanceMargin(), 0.001);
        assertEquals(VisionLocalizedOwnershipGuard.COMPETING_CONNECTOR_CLOSER, result.deterministicOwnershipReason());
    }

    @Test
    void doesNotVetoForInsufficientDistanceDifference() {
        VisionLocalizedOwnershipGuard.Evaluation result = evaluate(10, 15, List.of(guide("E2", 0, 5)));

        assertEquals("BELONGS", result.effectiveOwnership());
        assertEquals(15.0, result.currentConnectorDistance(), 0.001);
        assertEquals(10.0, result.nearestCompetingDistance(), 0.001);
        assertNull(result.deterministicOwnershipReason());
    }

    @Test
    void doesNotVetoWithoutCompetitorOrCurrentGuide() {
        assertEquals("BELONGS", evaluate(10, 5, List.of()).effectiveOwnership());
        VisionRelationshipEvidenceSheetRenderer.OwnershipConnectorGuides noCurrent =
                new VisionRelationshipEvidenceSheetRenderer.OwnershipConnectorGuides(null, List.of(guide("E2", 0, 25)), 256);
        assertEquals("BELONGS", VisionLocalizedOwnershipGuard.evaluateLocalizedOwnership(
                "BELONGS", new VisionOwnershipPanelPoint(10, 30), noCurrent
        ).effectiveOwnership());
    }

    private VisionLocalizedOwnershipGuard.Evaluation evaluate(
            double x, double y, List<VisionLocalizedOwnershipGuard.ConnectorGuide> competitors
    ) {
        return VisionLocalizedOwnershipGuard.evaluateLocalizedOwnership(
                "BELONGS", new VisionOwnershipPanelPoint(x, y),
                new VisionRelationshipEvidenceSheetRenderer.OwnershipConnectorGuides(guide("E1", 0, 0), competitors, 267)
        );
    }

    private VisionLocalizedOwnershipGuard.ConnectorGuide guide(String edgeId, double x, double y) {
        return new VisionLocalizedOwnershipGuard.ConnectorGuide(
                edgeId, new VisionOwnershipPanelPoint(x, y), new VisionOwnershipPanelPoint(x + 20, y)
        );
    }
}
