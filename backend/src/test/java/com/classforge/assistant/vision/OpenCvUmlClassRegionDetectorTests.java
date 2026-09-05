package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCvUmlClassRegionDetectorTests {

    @Test
    void detectsOuterClassRectanglesAndSuppressesCompartments() throws Exception {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(5));
            drawClass(g, 70, 80, 180, 190);
            drawClass(g, 360, 70, 210, 220);
            drawClass(g, 620, 300, 190, 200);
            // Relationship lines must not become class boxes.
            g.drawLine(250, 170, 360, 170);
            g.drawLine(570, 210, 690, 300);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] bytes = out.toByteArray();
        VisionNormalizedImage normalized = new VisionNormalizedImage(
                "synthetic.png", "image/png", "image/png", bytes,
                image.getWidth(), image.getHeight(), "synthetic", true
        );

        UmlClassRegionDetection detection = new OpenCvUmlClassRegionDetector().detect(normalized, 3);
        assertEquals(3, detection.safeRegions().size());
        assertFalse(detection.thresholdPng().length == 0);
        assertFalse(detection.overlayPng().length == 0);
        assertTrue(detection.safeRegions().stream().allMatch(r -> r.classRef() == null));
    }

    @Test
    void detectsSixPhysicalBoxesInRealisticWhiteboardFixture() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream(
                "/assistant/vision/benchmark/hardening/library-whiteboard-realistic.png"
        )) {
            if (input == null) {
                throw new IllegalStateException("Missing realistic whiteboard fixture");
            }
            bytes = input.readAllBytes();
        }
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        VisionNormalizedImage normalized = new VisionNormalizedImage(
                "library-whiteboard-realistic.png", "image/png", "image/png", bytes,
                image.getWidth(), image.getHeight(), "fixture", false
        );

        UmlClassRegionDetection detection = new OpenCvUmlClassRegionDetector().detect(normalized, 6);
        assertEquals(6, detection.safeRegions().size());
        List<Rect> expected = List.of(
                new Rect(347, 179, 170, 294),
                new Rect(540, 232, 269, 360),
                new Rect(380, 720, 215, 250),
                new Rect(649, 750, 204, 252),
                new Rect(482, 1132, 297, 352),
                new Rect(243, 1199, 222, 263)
        );
        assertEquals(List.of("B1", "B2", "B3", "B4", "B5", "B6"), detection.safeRegions().stream()
                .map(VisionGeometryClassRegion::geometryId)
                .toList());
        assertTrue(detection.safeRegions().stream().allMatch(region -> region.classRef() == null));
        assertAll(java.util.stream.IntStream.range(0, expected.size()).mapToObj(index -> () -> {
            VisionGeometryClassRegion actual = detection.safeRegions().get(index);
            Rect oracle = expected.get(index);
            double iou = intersectionOverUnion(actual, oracle);
            assertTrue(iou >= 0.82, () -> actual.geometryId()
                    + " expected=" + oracle.x + "," + oracle.y + " " + oracle.width + "x" + oracle.height
                    + " actual=" + actual.x() + "," + actual.y() + " " + actual.width() + "x" + actual.height()
                    + " IoU=" + iou);
        }));
        assertFalse(detection.thresholdPng().length == 0);
        assertFalse(detection.overlayPng().length == 0);
    }

    @Test
    void chainsStrictPrimitivesAcrossSuccessiveCompartments() throws Exception {
        BufferedImage image = new BufferedImage(2000, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4));
            // Small compartments are deliberately separated by five pixels: they
            // remain distinct strict contours while representing one class chain.
            g.drawRect(100, 70, 70, 200);
            g.drawRect(175, 70, 200, 200);
            g.drawRect(380, 70, 70, 200);
            g.drawRect(455, 70, 70, 200);
            g.drawRect(530, 70, 70, 200);
            // This divider makes the central strict primitive a complete-looking
            // alternative; the chain must nevertheless continue to the last box.
            g.drawLine(275, 70, 275, 270);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        VisionNormalizedImage normalized = new VisionNormalizedImage(
                "strict-chain.png", "image/png", "image/png", out.toByteArray(),
                image.getWidth(), image.getHeight(), "synthetic", true
        );

        UmlClassRegionDetection detection = new OpenCvUmlClassRegionDetector().detect(normalized, 1);
        assertEquals(1, detection.safeRegions().size());
        VisionGeometryClassRegion region = detection.safeRegions().getFirst();
        double iou = intersectionOverUnion(region, new Rect(100, 70, 500, 200));
        assertTrue(iou >= 0.82, () -> "actual=" + region.x() + "," + region.y()
                + " " + region.width() + "x" + region.height() + " IoU=" + iou);
    }

    @Test
    void acceptsOneValidHoughProjectedCornerWithSyntheticIouOracle() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4));
            g.drawRect(100, 100, 100, 200);
            g.drawLine(200, 100, 270, 100);
            g.drawLine(270, 100, 270, 300);
        } finally {
            g.dispose();
        }

        VisionGeometryClassRegion region = detectSingle(image, "one-hough-corner.png");
        assertSyntheticIouAtLeast(region, new Rect(100, 100, 170, 200));
    }

    @Test
    void acceptsCompanionDepthWithinCurrentShortSidePlusAdjacencyTolerance() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4));
            g.drawRect(100, 100, 100, 200);
            g.drawRect(205, 100, 100, 200);
        } finally {
            g.dispose();
        }

        VisionGeometryClassRegion region = detectSingle(image, "adjacent-depth.png");
        assertSyntheticIouAtLeast(region, new Rect(100, 100, 205, 200));
    }

    @Test
    void rejectsIncompleteOneSourcePrimitiveFromFinalSelection() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(5));
            g.drawRect(180, 120, 220, 260);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        VisionNormalizedImage normalized = new VisionNormalizedImage(
                "incomplete-one-source.png", "image/png", "image/png", out.toByteArray(),
                image.getWidth(), image.getHeight(), "synthetic", true
        );

        assertThrows(AssistantPlanningException.class,
                () -> new OpenCvUmlClassRegionDetector().detect(normalized, 1));
    }

    private void drawClass(Graphics2D g, int x, int y, int width, int height) {
        g.drawRect(x, y, width, height);
        g.drawLine(x, y + 55, x + width, y + 55);
        g.drawLine(x, y + 120, x + width, y + 120);
    }

    @Test
    void rejectsLongOuterBorderAsInternalDivider() {
        Mat binary = frame(180, false, true, true);
        try {
            assertFalse(new OpenCvUmlClassRegionDetector().hasInternalDivider(binary, new Rect(60, 50, 120, 200), 300));
        } finally {
            binary.release();
        }
    }

    @Test
    void acceptsTrueInteriorDivider() {
        Mat binary = frame(120, true, true, true);
        try {
            assertTrue(new OpenCvUmlClassRegionDetector().hasInternalDivider(binary, new Rect(60, 50, 120, 200), 300));
        } finally {
            binary.release();
        }
    }

    @Test
    void rejectsNearBorderDividerEvenWhenLongAndParallel() {
        Mat binary = frame(70, true, true, true);
        try {
            assertFalse(new OpenCvUmlClassRegionDetector().hasInternalDivider(binary, new Rect(60, 50, 120, 200), 300));
        } finally {
            binary.release();
        }
    }

    @Test
    void acceptsBrokenHandDrawnFrameWithOneSidedEndpointNoise() {
        Mat binary = frame(120, true, false, true);
        try {
            assertTrue(new OpenCvUmlClassRegionDetector().hasInternalDivider(binary, new Rect(60, 50, 120, 200), 300));
        } finally {
            binary.release();
        }
    }

    @Test
    void acceptsProjectedCornerBinaryBridgeWhenHoughContinuationIsAbsent() {
        OpenCV.loadLocally();
        Mat binary = Mat.zeros(300, 300, CvType.CV_8UC1);
        try {
            Scalar ink = Scalar.all(255);
            Imgproc.line(binary, new Point(60, 50), new Point(180, 50), ink, 3);
            Imgproc.line(binary, new Point(60, 250), new Point(180, 250), ink, 3);
            assertTrue(new OpenCvUmlClassRegionDetector().hasBinaryProjectedCornerBridge(
                    binary, new Rect(60, 50, 120, 200), 1, 180, 50
            ));
            assertFalse(new OpenCvUmlClassRegionDetector().hasBinaryProjectedCornerBridge(
                    binary, new Rect(60, 50, 120, 200), -1, 40, 250
            ));
        } finally {
            binary.release();
        }
    }

    private Mat frame(int dividerX, boolean drawDivider, boolean topSupported, boolean bottomSupported) {
        OpenCV.loadLocally();
        Mat binary = Mat.zeros(300, 300, CvType.CV_8UC1);
        Scalar ink = Scalar.all(255);
        Imgproc.line(binary, new Point(60, 50), new Point(60, 250), ink, 3);
        Imgproc.line(binary, new Point(180, 50), new Point(180, 250), ink, 3);
        if (topSupported) {
            Imgproc.line(binary, new Point(60, 50), new Point(180, 50), ink, 3);
        }
        if (bottomSupported) {
            Imgproc.line(binary, new Point(60, 250), new Point(180, 250), ink, 3);
        }
        if (drawDivider) {
            Imgproc.line(binary, new Point(dividerX, 50), new Point(dividerX, 250), ink, 3);
        }
        return binary;
    }

    private double intersectionOverUnion(VisionGeometryClassRegion actual, Rect expected) {
        int left = Math.max(actual.x(), expected.x);
        int top = Math.max(actual.y(), expected.y);
        int right = Math.min(actual.right(), expected.x + expected.width);
        int bottom = Math.min(actual.bottom(), expected.y + expected.height);
        if (right <= left || bottom <= top) {
            return 0.0;
        }
        long intersection = (long) (right - left) * (bottom - top);
        long actualArea = (long) actual.width() * actual.height();
        long expectedArea = (long) expected.width * expected.height;
        return intersection / (double) (actualArea + expectedArea - intersection);
    }

    private VisionGeometryClassRegion detectSingle(BufferedImage image, String filename) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        UmlClassRegionDetection detection = new OpenCvUmlClassRegionDetector().detect(new VisionNormalizedImage(
                filename, "image/png", "image/png", out.toByteArray(),
                image.getWidth(), image.getHeight(), "synthetic", true
        ), 1);
        assertEquals(1, detection.safeRegions().size());
        return detection.safeRegions().getFirst();
    }

    private void assertSyntheticIouAtLeast(VisionGeometryClassRegion actual, Rect expected) {
        double iou = intersectionOverUnion(actual, expected);
        assertTrue(iou >= 0.90, () -> "expected=" + expected.x + "," + expected.y
                + " " + expected.width + "x" + expected.height + " actual=" + actual.x() + "," + actual.y()
                + " " + actual.width() + "x" + actual.height() + " IoU=" + iou);
    }
}
