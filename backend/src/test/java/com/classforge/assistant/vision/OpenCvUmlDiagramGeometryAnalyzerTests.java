package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCvUmlDiagramGeometryAnalyzerTests {

    @Test
    void emitsOnlyGeometrySupportedPairsAndDoesNotTurnCrossingsIntoJunctions() throws Exception {
        BufferedImage image = new BufferedImage(620, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(40, 70, 130, 110);
        g.drawRect(350, 70, 130, 110);
        g.drawRect(240, 235, 130, 95);
        // c1 -- c2
        g.drawLine(170, 125, 350, 125);
        // A crossing stroke whose intersection is not a segment endpoint. It must
        // not create a false connection to c3.
        g.drawLine(260, 25, 260, 220);
        g.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        VisionNormalizedImage normalized = new VisionNormalizedImage(
                "synthetic.png", "image/png", "image/png", output.toByteArray(),
                620, 360, "test", false
        );
        VisionClassLocalizationProposal localization = new VisionClassLocalizationProposal(
                List.of(
                        new VisionClassLocalization("c1", 40, 70, 130, 110, 1.0),
                        new VisionClassLocalization("c2", 350, 70, 130, 110, 1.0),
                        new VisionClassLocalization("c3", 240, 235, 130, 95, 1.0)
                ),
                List.of(), 1.0
        );

        UmlDiagramGeometry geometry = new OpenCvUmlDiagramGeometryAnalyzer().analyze(normalized, localization);
        assertEquals(3, geometry.safeClassRegions().size());
        assertEquals(1, geometry.safeEdgeCandidates().size());
        assertEquals("c1", geometry.safeEdgeCandidates().getFirst().aClassRef());
        assertEquals("c2", geometry.safeEdgeCandidates().getFirst().bClassRef());
        assertTrue(geometry.safeEdgeCandidates().getFirst().geometryScore() > 0.0);
        assertTrue(geometry.safeEdgeCandidates().getFirst().innerAX() != null);
        assertTrue(geometry.safeEdgeCandidates().getFirst().innerBX() != null);
        VisionGeometryEdgeCandidate edge = geometry.safeEdgeCandidates().getFirst();
        assertTrue(innerX(edge, "c1") > contactX(edge, "c1"));
        assertTrue(innerX(edge, "c2") < contactX(edge, "c2"));
        assertFalse(geometry.thresholdPng().length == 0);
        assertFalse(geometry.segmentsPng().length == 0);
        assertTrue(geometry.overlayPng().length > 0);
    }

    @Test
    void bridgesSmallEndpointGapWhenRasterSupportsConnector() {
        OpenCV.loadLocally();
        Mat closed = Mat.zeros(32, 32, CvType.CV_8UC1);
        try {
            Point a = new Point(10, 16);
            Point b = new Point(18, 16);
            Imgproc.line(closed, a, b, Scalar.all(255), 1);

            OpenCvUmlDiagramGeometryAnalyzer.RasterEndpointBridge bridge =
                    new OpenCvUmlDiagramGeometryAnalyzer().evaluateRasterEndpointBridge(closed, a, b, 10.0);

            assertEquals(8.0, bridge.distance());
            assertEquals(1.0, bridge.rasterCoverage());
            assertTrue(bridge.accepted());
            assertEquals(null, bridge.rejectReason());
        } finally {
            closed.release();
        }
    }

    @Test
    void doesNotBridgeNearbyEndpointsWithoutRasterSupport() {
        OpenCV.loadLocally();
        Mat closed = Mat.zeros(32, 32, CvType.CV_8UC1);
        try {
            Point a = new Point(10, 16);
            Point b = new Point(18, 16);
            closed.put(16, 10, 255);
            closed.put(16, 18, 255);

            OpenCvUmlDiagramGeometryAnalyzer.RasterEndpointBridge bridge =
                    new OpenCvUmlDiagramGeometryAnalyzer().evaluateRasterEndpointBridge(closed, a, b, 10.0);

            assertEquals(8.0, bridge.distance());
            assertTrue(bridge.rasterCoverage() < 0.75);
            assertFalse(bridge.accepted());
            assertEquals("insufficient-raster-coverage", bridge.rejectReason());
        } finally {
            closed.release();
        }
    }

    @Test
    void usesSmallRasterMarkerToRecoverClassAttachment() throws Exception {
        BufferedImage image = canvas(720, 720);
        Graphics2D g = image.createGraphics();
        try {
            prepare(g);
            g.drawRect(300, 50, 100, 100);
            g.drawRect(300, 570, 100, 100);
            // The lower Hough endpoint is 30px below B1, but its local raster
            // neighborhood reaches the marker tip 3px below B1's masked border.
            g.drawLine(350, 153, 365, 168);
            g.drawLine(365, 168, 350, 183);
            g.drawLine(350, 183, 335, 168);
            g.drawLine(335, 168, 350, 153);
            g.drawLine(350, 183, 350, 570);
        } finally {
            g.dispose();
        }

        UmlDiagramGeometry geometry = analyze(image, "raster-marker.png", List.of(
                new VisionClassLocalization("c1", 300, 50, 100, 100, 1.0),
                new VisionClassLocalization("c2", 300, 570, 100, 100, 1.0)
        ));

        assertEquals(1, geometry.safeEdgeCandidates().size());
        VisionGeometryEdgeCandidate edge = geometry.safeEdgeCandidates().getFirst();
        assertEquals(Set.of("c1", "c2"), Set.of(edge.aClassRef(), edge.bClassRef()));
        // B1 is attached by its local marker; B2 remains the direct far endpoint.
        assertTrue(contactY(edge, "c1") <= 155);
        assertTrue(contactY(edge, "c2") >= 560);
        assertTrue(innerY(edge, "c1") != null);
    }

    @Test
    void ambiguousRasterComponentDoesNotPropagateAttachment() throws Exception {
        BufferedImage image = canvas(720, 720);
        Graphics2D g = image.createGraphics();
        try {
            prepare(g);
            g.drawRect(250, 50, 100, 100);
            g.drawRect(370, 50, 100, 100);
            g.drawRect(310, 570, 100, 100);
            g.drawLine(360, 153, 375, 168);
            g.drawLine(375, 168, 360, 183);
            g.drawLine(360, 183, 345, 168);
            g.drawLine(345, 168, 360, 153);
            g.drawLine(360, 183, 360, 570);
        } finally {
            g.dispose();
        }

        UmlDiagramGeometry geometry = analyze(image, "ambiguous-raster-marker.png", List.of(
                new VisionClassLocalization("c1", 250, 50, 100, 100, 1.0),
                new VisionClassLocalization("c2", 370, 50, 100, 100, 1.0),
                new VisionClassLocalization("c3", 310, 570, 100, 100, 1.0)
        ));

        assertFalse(geometry.safeEdgeCandidates().stream().anyMatch(edge ->
                (edge.aClassRef().equals("c1") || edge.aClassRef().equals("c2"))
                        && edge.bClassRef().equals("c3")
        ));
    }

    @Test
    void doesNotLetLocalRasterThirdClassInvalidateDirectBinaryPair() {
        OpenCvUmlDiagramGeometryAnalyzer analyzer = new OpenCvUmlDiagramGeometryAnalyzer();

        OpenCvUmlDiagramGeometryAnalyzer.LocalAttachmentDecision directPairDecision =
                analyzer.evaluateLocalAttachment(Set.of("c1", "c2"), Set.of(), "c3");
        assertFalse(directPairDecision.accepted());
        assertEquals("DIRECT_PAIR_ALREADY_COMPLETE", directPairDecision.result());
        assertEquals(Set.of("c1", "c2", "c3"), directPairDecision.prospectiveTouched());

        OpenCvUmlDiagramGeometryAnalyzer.LocalAttachmentDecision cardinalityDecision =
                analyzer.evaluateLocalAttachment(Set.of("c1"), Set.of("c2"), "c3");
        assertFalse(cardinalityDecision.accepted());
        assertEquals("COMPONENT_CLASS_CARDINALITY_GUARD", cardinalityDecision.result());
        assertEquals(Set.of("c1", "c2", "c3"), cardinalityDecision.prospectiveTouched());
    }

    @Test
    void writesRealisticLibraryWhiteboardGraphDiagnostics() throws Exception {
        byte[] bytes;
        try (InputStream fixture = getClass().getResourceAsStream(
                "/assistant/vision/benchmark/hardening/library-whiteboard-realistic.png"
        )) {
            assertTrue(fixture != null);
            bytes = fixture.readAllBytes();
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", "c1", 349, 182, 166, 290, 1.0),
                new VisionGeometryClassRegion("B2", "c2", 542, 234, 254, 350, 1.0),
                new VisionGeometryClassRegion("B3", "c3", 383, 723, 211, 245, 1.0),
                new VisionGeometryClassRegion("B4", "c4", 652, 753, 199, 246, 1.0),
                new VisionGeometryClassRegion("B5", "c5", 485, 1134, 291, 347, 1.0),
                new VisionGeometryClassRegion("B6", "c6", 246, 1203, 215, 255, 1.0)
        );

        UmlDiagramGeometry geometry = new OpenCvUmlDiagramGeometryAnalyzer().analyze(
                new VisionNormalizedImage("library-whiteboard-realistic", "image/png", "image/png", bytes,
                        image.getWidth(), image.getHeight(), "test", false),
                regions
        );

        assertEquals(6, geometry.safeClassRegions().size());
        assertEquals(Set.of("B1|B5", "B2|B3", "B2|B5", "B3|B4", "B4|B5", "B5|B6"),
                geometry.safeEdgeCandidates().stream()
                        .map(edge -> pair(edge.aGeometryId(), edge.bGeometryId()))
                        .collect(java.util.stream.Collectors.toSet()));
        assertFalse(geometry.thresholdPng().length == 0);
        assertFalse(geometry.segmentsPng().length == 0);
        assertFalse(geometry.overlayPng().length == 0);
    }

    private BufferedImage canvas(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private void prepare(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 720, 720);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4f));
    }

    private UmlDiagramGeometry analyze(
            BufferedImage image,
            String filename,
            List<VisionClassLocalization> classes
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new OpenCvUmlDiagramGeometryAnalyzer().analyze(
                new VisionNormalizedImage(filename, "image/png", "image/png", output.toByteArray(),
                        image.getWidth(), image.getHeight(), "test", false),
                new VisionClassLocalizationProposal(classes, List.of(), 1.0)
        );
    }

    private int contactY(VisionGeometryEdgeCandidate edge, String classRef) {
        return edge.aClassRef().equals(classRef) ? edge.contactAY() : edge.contactBY();
    }

    private Integer innerY(VisionGeometryEdgeCandidate edge, String classRef) {
        return edge.aClassRef().equals(classRef) ? edge.innerAY() : edge.innerBY();
    }

    @Test
    void optionalDirectionMetadataDoesNotRemoveAnAlreadySelectedEdge() {
        VisionGeometryEdgeCandidate edge = new VisionGeometryEdgeCandidate(
                "E1", "B1", "B2", "c1", "c2", 0.5, 0, 0, 100, 100, 10, 10, 90, 90
        );
        UmlDiagramGeometry geometry = new UmlDiagramGeometry(List.of(), List.of(edge), new byte[0], new byte[0], new byte[0]);

        assertEquals(1, geometry.safeEdgeCandidates().size());
        assertEquals(null, edge.innerAX());
        assertEquals(null, edge.innerBX());
    }

    private int contactX(VisionGeometryEdgeCandidate edge, String classRef) {
        return edge.aClassRef().equals(classRef) ? edge.contactAX() : edge.contactBX();
    }

    private Integer innerX(VisionGeometryEdgeCandidate edge, String classRef) {
        return edge.aClassRef().equals(classRef) ? edge.innerAX() : edge.innerBX();
    }

    private String pair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
