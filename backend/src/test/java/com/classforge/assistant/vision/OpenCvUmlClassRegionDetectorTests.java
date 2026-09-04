package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(detection.safeRegions().stream().allMatch(r -> r.width() >= 100 && r.height() >= 190));
    }

    private void drawClass(Graphics2D g, int x, int y, int width, int height) {
        g.drawRect(x, y, width, height);
        g.drawLine(x, y + 55, x + width, y + 55);
        g.drawLine(x, y + 120, x + width, y + 120);
    }
}
