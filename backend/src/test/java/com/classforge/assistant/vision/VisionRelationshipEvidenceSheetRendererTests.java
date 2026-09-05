package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionRelationshipEvidenceSheetRendererTests {

    @Test
    void keepsCompetitorWhenCenterlineIsOutsideButFootprintEntersCrop() {
        int pixels = VisionRelationshipEvidenceSheetRenderer.competitorMaskPixelCountForTesting(
                150, 150, -5, 75, -5, 103
        );

        assertTrue(pixels > 0);
    }

    @Test
    void ignoresCompetitorWhoseFootprintIsCompletelyDistant() {
        int pixels = VisionRelationshipEvidenceSheetRenderer.competitorMaskPixelCountForTesting(
                150, 150, -100, 75, -100, 103
        );

        assertEquals(0, pixels);
    }

    @Test
    void connectorCorridorFollowsDirectionAndRemainsWithinCropBounds() {
        VisionRelationshipEvidenceSheetRenderer.ConnectorCorridor upward =
                VisionRelationshipEvidenceSheetRenderer.connectorCorridor(new Point(80, 80), new Point(80, 52), 160, 160);
        VisionRelationshipEvidenceSheetRenderer.ConnectorCorridor rightward =
                VisionRelationshipEvidenceSheetRenderer.connectorCorridor(new Point(80, 80), new Point(108, 80), 160, 160);

        assertNotNull(upward);
        assertNotNull(rightward);
        assertEquals(80.0, upward.endX());
        assertTrue(upward.endY() < upward.startY());
        assertEquals(80.0, rightward.endY());
        assertTrue(rightward.endX() > rightward.startX());
        assertEquals(36.0, upward.halfWidth());
        assertEquals(40.0, upward.contactRadius());
        assertInside(upward, 160, 160);
        assertInside(rightward, 160, 160);
    }

    @Test
    void rendersGuidedOwnershipPanelWithConnectorAnnotations() throws Exception {
        VisionNormalizedImage panel = new VisionRelationshipEvidenceSheetRenderer().renderMultiplicityOwnership(
                source(), edgeWithDirection(), VisionHybridEndpoint.B,
                new VisionClassProposal("c2", "Prestamo", java.util.List.of(), null),
                new VisionGeometryClassRegion("B2", "c2", 110, 20, 50, 80, 1.0),
                java.util.List.of(edgeWithDirection())
        );

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(panel.bytes()));
        assertEquals(384, panel.width());
        assertEquals(360, panel.height());
        assertEquals("multiplicity-E2-B-ownership.png", panel.originalFilename());
        assertTrue(hasRedMarker(decoded, 0, 256));
    }

    @Test
    void rendersOnlyVisibleSameClassCompetitorsInBlue() throws Exception {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        VisionNormalizedImage source = blankSource(400, 240);
        VisionGeometryEdgeCandidate current = edge(
                "E1", "c1", "c2", 100, 100, 220, 100, 128, 100, 192, 100
        );
        VisionGeometryEdgeCandidate competitor = edge(
                "E2", "c1", "c3", 100, 125, 280, 125, 128, 125, 252, 125
        );
        VisionGeometryEdgeCandidate unrelated = edge(
                "E3", "c4", "c5", 100, 145, 300, 145, 128, 145, 272, 145
        );
        List<VisionGeometryEdgeCandidate> edges = List.of(unrelated, competitor, current);

        VisionNormalizedImage panel = renderer.renderMultiplicityOwnership(
                source, current, VisionHybridEndpoint.A,
                new VisionClassProposal("c1", "Biblioteca", List.of(), null), regionC1(), edges
        );
        VisionRelationshipEvidenceSheetRenderer.OwnershipRenderingMetadata metadata =
                renderer.ownershipRenderingMetadata(source, current, VisionHybridEndpoint.A, "c1", regionC1(), edges);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(panel.bytes()));
        VisionOwnershipPanelPoint currentContact = metadata.current().contactPanel();
        VisionOwnershipPanelPoint competitorContact = metadata.competitors().getFirst().contactPanel();

        assertNotNull(decoded);
        assertEquals(384, decoded.getWidth());
        assertEquals(360, decoded.getHeight());
        assertEquals(List.of("E2"), metadata.competitors().stream().map(
                VisionRelationshipEvidenceSheetRenderer.OwnershipEndpointOverlay::edgeId
        ).toList());
        assertTrue(hasRedNear(decoded, roundedX(currentContact), roundedY(currentContact)));
        assertTrue(hasBlueNear(decoded, roundedX(competitorContact), roundedY(competitorContact)));
        assertFalse(hasBlueNear(decoded, roundedX(currentContact), roundedY(currentContact)));
    }

    @Test
    void classContextIncludesAllIncidentEdgesAcrossTheClass() throws Exception {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        VisionNormalizedImage source = blankSource(420, 320);
        VisionGeometryClassRegion region = new VisionGeometryClassRegion("B1", "c1", 100, 100, 200, 120, 1.0);
        VisionGeometryEdgeCandidate current = edge(
                "E1", "c1", "c2", 200, 220, 200, 300, 200, 192, 200, 272
        );
        VisionGeometryEdgeCandidate left = edge(
                "E2", "c1", "c3", 100, 160, 20, 160, 128, 160, 48, 160
        );
        VisionGeometryEdgeCandidate right = edge(
                "E3", "c4", "c1", 380, 160, 300, 160, 352, 160, 272, 160
        );
        VisionGeometryEdgeCandidate unrelated = edge(
                "E4", "c5", "c6", 50, 30, 370, 30, 78, 30, 342, 30
        );
        List<VisionGeometryEdgeCandidate> edges = List.of(unrelated, right, current, left);

        VisionNormalizedImage panel = renderer.renderMultiplicityOwnership(
                source, current, VisionHybridEndpoint.A,
                new VisionClassProposal("c1", "Biblioteca", List.of(), null), region, edges
        );
        VisionRelationshipEvidenceSheetRenderer.OwnershipRenderingMetadata metadata =
                renderer.ownershipRenderingMetadata(source, current, VisionHybridEndpoint.A, "c1", region, edges);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(panel.bytes()));

        assertEquals(List.of("E2", "E3"), metadata.competitors().stream().map(
                VisionRelationshipEvidenceSheetRenderer.OwnershipEndpointOverlay::edgeId
        ).toList());
        assertTrue(hasRedNear(decoded, roundedX(metadata.current().contactPanel()), roundedY(metadata.current().contactPanel())));
        for (VisionRelationshipEvidenceSheetRenderer.OwnershipEndpointOverlay competitor : metadata.competitors()) {
            assertTrue(hasBlueNear(decoded, roundedX(competitor.contactPanel()), roundedY(competitor.contactPanel())));
        }
        assertTrue(metadata.crop().x() <= region.x());
        assertTrue(metadata.crop().y() <= region.y());
        assertTrue(metadata.crop().x() + metadata.crop().image().getWidth() >= region.right());
        assertTrue(metadata.crop().y() + metadata.crop().image().getHeight() >= region.bottom());
    }

    @Test
    void rendersCombinedAttributionPanelForCurrentAndCompetitors() throws Exception {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        VisionNormalizedImage source = blankSource(400, 240);
        VisionGeometryClassRegion region = regionC1();
        VisionGeometryEdgeCandidate current = edge("E6", "c1", "c2", 100, 100, 220, 100, 128, 100, 192, 100);
        VisionGeometryEdgeCandidate competitor = edge("E4", "c1", "c3", 100, 125, 280, 125, 128, 125, 252, 125);

        VisionNormalizedImage panel = renderer.renderMultiplicityAttribution(
                source, current, VisionHybridEndpoint.A, new VisionClassProposal("c1", "Biblioteca", List.of(), null),
                region, List.of(current, competitor), "1"
        );
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(panel.bytes()));

        assertEquals("multiplicity-E6-A-attribution.png", panel.originalFilename());
        assertEquals(640, decoded.getWidth());
        assertEquals(360, decoded.getHeight());
        assertTrue(hasRedMarker(decoded, 256, 640));
        assertTrue(hasBlueNear(decoded, 433, 219));
    }

    @Test
    void selectsCompetingEndpointGeometryFromEitherCandidateSideInEdgeIdOrder() {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        VisionGeometryEdgeCandidate current = edge(
                "E1", "c1", "c2", 100, 100, 220, 100, 128, 100, 192, 100
        );
        VisionGeometryEdgeCandidate classAtA = edge(
                "E2", "c1", "c3", 101, 121, 281, 121, 129, 121, 253, 121
        );
        VisionGeometryEdgeCandidate classAtB = edge(
                "E3", "c4", "c1", 301, 79, 99, 79, 273, 79, 127, 79
        );

        List<VisionRelationshipEvidenceSheetRenderer.CompetingEndpoint> selected = renderer.competingEndpoints(
                current, "c1", List.of(classAtB, current, classAtA)
        );

        assertEquals(List.of("E2", "E3"), selected.stream().map(
                VisionRelationshipEvidenceSheetRenderer.CompetingEndpoint::edgeId
        ).toList());
        assertEquals(VisionHybridEndpoint.A, selected.get(0).endpoint());
        assertEquals(101, selected.get(0).endpointData().contactX());
        assertEquals(129, selected.get(0).endpointData().innerX());
        assertEquals(VisionHybridEndpoint.B, selected.get(1).endpoint());
        assertEquals(99, selected.get(1).endpointData().contactX());
        assertEquals(127, selected.get(1).endpointData().innerX());
    }

    @Test
    void excludesDistantSameClassCompetitorFromRendering() throws Exception {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        VisionNormalizedImage source = blankSource(400, 240);
        VisionGeometryEdgeCandidate current = edge(
                "E1", "c1", "c2", 100, 100, 220, 100, 128, 100, 192, 100
        );
        VisionGeometryEdgeCandidate distant = edge(
                "E9", "c1", "c3", 330, 210, 380, 210, 358, 210, 352, 210
        );
        List<VisionGeometryEdgeCandidate> edges = List.of(current, distant);

        VisionRelationshipEvidenceSheetRenderer.OwnershipRenderingMetadata metadata =
                renderer.ownershipRenderingMetadata(source, current, VisionHybridEndpoint.A, "c1", regionC1(), edges);
        assertTrue(metadata.competitors().isEmpty());
    }

    private VisionGeometryEdgeCandidate edgeWithDirection() {
        return new VisionGeometryEdgeCandidate(
                "E2", "B1", "B2", "c1", "c2", 1.0, 0, 0, 180, 120, 40, 60, 140, 60,
                68, 60, 112, 60
        );
    }

    private VisionGeometryClassRegion regionC1() {
        return new VisionGeometryClassRegion("B1", "c1", 50, 50, 100, 100, 1.0);
    }

    private VisionGeometryEdgeCandidate edge(
            String edgeId,
            String aClassRef,
            String bClassRef,
            int contactAX,
            int contactAY,
            int contactBX,
            int contactBY,
            Integer innerAX,
            Integer innerAY,
            Integer innerBX,
            Integer innerBY
    ) {
        return new VisionGeometryEdgeCandidate(
                edgeId, "B1", "B2", aClassRef, bClassRef, 1.0,
                0, 0, 400, 240, contactAX, contactAY, contactBX, contactBY,
                innerAX, innerAY, innerBX, innerBY
        );
    }

    private VisionNormalizedImage source() throws Exception {
        BufferedImage image = new BufferedImage(180, 120, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLUE);
        graphics.fillRect(138, 58, 5, 5);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new VisionNormalizedImage("source.png", "image/png", "image/png", output.toByteArray(), 180, 120, "test", false);
    }

    private VisionNormalizedImage blankSource(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new VisionNormalizedImage(
                "blank.png", "image/png", "image/png", output.toByteArray(), width, height, "test", false
        );
    }

    private int roundedX(VisionOwnershipPanelPoint point) {
        return (int) Math.round(point.x());
    }

    private int roundedY(VisionOwnershipPanelPoint point) {
        return (int) Math.round(point.y());
    }

    private boolean hasRedMarker(BufferedImage image, int startX, int endX) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = startX; x < endX; x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() > 180 && color.getGreen() < 100 && color.getBlue() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRedNear(BufferedImage image, int centerX, int centerY) {
        for (int y = Math.max(0, centerY - 8); y <= Math.min(image.getHeight() - 1, centerY + 8); y++) {
            for (int x = Math.max(0, centerX - 8); x <= Math.min(image.getWidth() - 1, centerX + 8); x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() > 180 && color.getGreen() < 100 && color.getBlue() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBlueNear(BufferedImage image, int centerX, int centerY) {
        for (int y = Math.max(0, centerY - 10); y <= Math.min(image.getHeight() - 1, centerY + 10); y++) {
            for (int x = Math.max(0, centerX - 10); x <= Math.min(image.getWidth() - 1, centerX + 10); x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getBlue() > 180 && color.getRed() < 100 && color.getGreen() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private void assertInside(VisionRelationshipEvidenceSheetRenderer.ConnectorCorridor corridor, int width, int height) {
        assertTrue(corridor.startX() >= 0 && corridor.startX() < width);
        assertTrue(corridor.endX() >= 0 && corridor.endX() < width);
        assertTrue(corridor.startY() >= 0 && corridor.startY() < height);
        assertTrue(corridor.endY() >= 0 && corridor.endY() < height);
    }
}
