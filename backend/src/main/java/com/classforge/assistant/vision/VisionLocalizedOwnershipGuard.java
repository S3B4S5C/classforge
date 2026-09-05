package com.classforge.assistant.vision;

import java.util.List;

final class VisionLocalizedOwnershipGuard {

    static final String COMPETING_CONNECTOR_CLOSER = "COMPETING_CONNECTOR_CLOSER";
    private static final double OWNERSHIP_GUIDE_LENGTH = 90.0;

    private VisionLocalizedOwnershipGuard() {
    }

    static VisionOwnershipPanelPoint toPanelPoint(int normalizedX, int normalizedY, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            throw new IllegalArgumentException("Ownership panel dimensions must be positive");
        }
        return new VisionOwnershipPanelPoint(
                normalizedX / 1000.0 * (panelWidth - 1),
                normalizedY / 1000.0 * (panelHeight - 1)
        );
    }

    static Evaluation evaluateLocalizedOwnership(
            String rawOwnership,
            VisionOwnershipPanelPoint labelPoint,
            VisionRelationshipEvidenceSheetRenderer.OwnershipConnectorGuides guides
    ) {
        double dominanceMargin = Math.max(6.0, guides.ownershipCropWidth() * 0.03);
        ConnectorGuide current = guides.currentGuide();
        if (current == null || labelPoint == null) {
            return new Evaluation(rawOwnership, null, null, null, dominanceMargin, null);
        }
        double currentDistance = pointToSegmentDistance(labelPoint, current);
        NearestCompetitor nearest = nearestCompetitor(labelPoint, guides.competingGuides());
        if (nearest == null) {
            return new Evaluation(rawOwnership, currentDistance, null, null, dominanceMargin, null);
        }
        boolean competitorDominates = nearest.distance() + dominanceMargin < currentDistance;
        String reason = "BELONGS".equals(rawOwnership) && competitorDominates
                ? COMPETING_CONNECTOR_CLOSER : null;
        String effectiveOwnership = reason == null ? rawOwnership : "NOT_BELONGS";
        return new Evaluation(
                effectiveOwnership, currentDistance, nearest.edgeId(), nearest.distance(), dominanceMargin, reason
        );
    }

    private static NearestCompetitor nearestCompetitor(
            VisionOwnershipPanelPoint point,
            List<ConnectorGuide> competitors
    ) {
        NearestCompetitor nearest = null;
        for (ConnectorGuide competitor : competitors) {
            double distance = pointToSegmentDistance(point, competitor);
            if (nearest == null || distance < nearest.distance()) {
                nearest = new NearestCompetitor(competitor.edgeId(), distance);
            }
        }
        return nearest;
    }

    private static double pointToSegmentDistance(VisionOwnershipPanelPoint point, ConnectorGuide guide) {
        double dx = guide.directionPanel().x() - guide.contactPanel().x();
        double dy = guide.directionPanel().y() - guide.contactPanel().y();
        double length = Math.hypot(dx, dy);
        if (length < 1e-6) {
            return Math.hypot(point.x() - guide.contactPanel().x(), point.y() - guide.contactPanel().y());
        }
        double endX = guide.contactPanel().x() + dx / length * OWNERSHIP_GUIDE_LENGTH;
        double endY = guide.contactPanel().y() + dy / length * OWNERSHIP_GUIDE_LENGTH;
        double segmentX = endX - guide.contactPanel().x();
        double segmentY = endY - guide.contactPanel().y();
        double projection = ((point.x() - guide.contactPanel().x()) * segmentX
                + (point.y() - guide.contactPanel().y()) * segmentY) / (OWNERSHIP_GUIDE_LENGTH * OWNERSHIP_GUIDE_LENGTH);
        double t = Math.max(0.0, Math.min(1.0, projection));
        return Math.hypot(
                point.x() - (guide.contactPanel().x() + t * segmentX),
                point.y() - (guide.contactPanel().y() + t * segmentY)
        );
    }

    record ConnectorGuide(String edgeId, VisionOwnershipPanelPoint contactPanel, VisionOwnershipPanelPoint directionPanel) {
    }

    record Evaluation(
            String effectiveOwnership,
            Double currentConnectorDistance,
            String nearestCompetingEdgeId,
            Double nearestCompetingDistance,
            Double dominanceMargin,
            String deterministicOwnershipReason
    ) {
    }

    private record NearestCompetitor(String edgeId, double distance) {
    }
}
