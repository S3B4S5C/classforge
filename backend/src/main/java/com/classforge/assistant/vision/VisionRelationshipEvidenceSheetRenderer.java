package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
public class VisionRelationshipEvidenceSheetRenderer {

    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 350;
    private static final int HEADER_HEIGHT = 34;
    private static final int PAIR_HEIGHT = 205;
    private static final int ENDPOINT_HEIGHT = 105;
    private static final int COLUMNS = 3;

    public VisionNormalizedImage render(
            VisionNormalizedImage source,
            UmlDiagramGeometry geometry
    ) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source.bytes()));
            if (original == null) {
                throw new AssistantPlanningException("No se pudo decodificar la imagen para crear evidence sheet.");
            }
            List<VisionGeometryEdgeCandidate> candidates = geometry.safeEdgeCandidates();
            int rows = Math.max(1, (int) Math.ceil(candidates.size() / (double) COLUMNS));
            BufferedImage sheet = new BufferedImage(
                    PANEL_WIDTH * COLUMNS,
                    PANEL_HEIGHT * rows,
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = sheet.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int index = 0;
                for (VisionGeometryEdgeCandidate edge : candidates) {
                    int col = index % COLUMNS;
                    int row = index / COLUMNS;
                    int ox = col * PANEL_WIDTH;
                    int oy = row * PANEL_HEIGHT;
                    drawPanel(graphics, original, edge, ox, oy);
                    index++;
                }
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(sheet, "png", output);
            byte[] bytes = output.toByteArray();
            return new VisionNormalizedImage(
                    "hybrid-relationship-evidence.png",
                    "image/png",
                    "image/png",
                    bytes,
                    sheet.getWidth(),
                    sheet.getHeight(),
                    sha256(bytes),
                    true
            );
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudo renderizar la hoja local de relaciones.", exception);
        }
    }

    private void drawPanel(
            Graphics2D graphics,
            BufferedImage original,
            VisionGeometryEdgeCandidate edge,
            int ox,
            int oy
    ) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(ox, oy, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.setColor(Color.BLACK);
        graphics.drawRect(ox, oy, PANEL_WIDTH - 1, PANEL_HEIGHT - 1);
        graphics.drawString(
                edge.edgeId() + "  A=" + edge.aClassRef() + "  B=" + edge.bClassRef()
                        + "  cv=" + String.format(java.util.Locale.ROOT, "%.2f", edge.geometryScore()),
                ox + 7,
                oy + 22
        );

        BufferedImage pair = crop(
                original,
                edge.cropX(), edge.cropY(), edge.cropWidth(), edge.cropHeight()
        );
        drawFit(graphics, pair, ox + 4, oy + HEADER_HEIGHT, PANEL_WIDTH - 8, PAIR_HEIGHT);

        int endpointWidth = (PANEL_WIDTH - 12) / 2;
        BufferedImage endpointA = endpointCrop(original, edge.contactAX(), edge.contactAY());
        BufferedImage endpointB = endpointCrop(original, edge.contactBX(), edge.contactBY());
        drawFit(graphics, endpointA, ox + 4, oy + HEADER_HEIGHT + PAIR_HEIGHT + 4, endpointWidth, ENDPOINT_HEIGHT);
        drawFit(graphics, endpointB, ox + 8 + endpointWidth, oy + HEADER_HEIGHT + PAIR_HEIGHT + 4, endpointWidth, ENDPOINT_HEIGHT);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawString("A", ox + 8, oy + PANEL_HEIGHT - 8);
        graphics.drawString("B", ox + 12 + endpointWidth, oy + PANEL_HEIGHT - 8);
    }

    private BufferedImage endpointCrop(BufferedImage source, int cx, int cy) {
        int radius = Math.max(70, Math.min(source.getWidth(), source.getHeight()) / 11);
        int x = Math.max(0, cx - radius);
        int y = Math.max(0, cy - radius);
        int right = Math.min(source.getWidth(), cx + radius);
        int bottom = Math.min(source.getHeight(), cy + radius);
        return crop(source, x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private BufferedImage crop(BufferedImage source, int x, int y, int width, int height) {
        int safeX = Math.max(0, Math.min(source.getWidth() - 1, x));
        int safeY = Math.max(0, Math.min(source.getHeight() - 1, y));
        int safeWidth = Math.max(1, Math.min(width, source.getWidth() - safeX));
        int safeHeight = Math.max(1, Math.min(height, source.getHeight() - safeY));
        return source.getSubimage(safeX, safeY, safeWidth, safeHeight);
    }

    private void drawFit(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int dx = x + (width - targetWidth) / 2;
        int dy = y + (height - targetHeight) / 2;
        graphics.setColor(new Color(245, 245, 245));
        graphics.fillRect(x, y, width, height);
        graphics.drawImage(image, dx, dy, targetWidth, targetHeight, null);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
