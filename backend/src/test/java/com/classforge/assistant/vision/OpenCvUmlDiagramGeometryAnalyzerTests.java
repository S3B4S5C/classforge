package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

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
        assertFalse(geometry.thresholdPng().length == 0);
        assertFalse(geometry.segmentsPng().length == 0);
        assertTrue(geometry.overlayPng().length > 0);
    }
}
