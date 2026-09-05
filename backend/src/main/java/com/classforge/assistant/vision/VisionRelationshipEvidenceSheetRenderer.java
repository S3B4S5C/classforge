package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int SINGLE_MULTIPLICITY_PANEL_WIDTH = 256;
    private static final int MULTIPLICITY_PANEL_HEIGHT = 280;
    private static final int MULTIPLICITY_HEADER_HEIGHT = 38;
    private static final int MULTIPLICITY_OWNERSHIP_WIDTH = 384;
    private static final int MULTIPLICITY_OWNERSHIP_HEIGHT = 360;
    private static final double CORRIDOR_MAX_LENGTH = 90.0;
    private static final double CORRIDOR_HALF_WIDTH = 36.0;
    private static final double CONTACT_ZONE_RADIUS = 40.0;
    private static final double COMPETITOR_CONTACT_ZONE_RADIUS = 28.0;

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

    public VisionNormalizedImage renderEdge(VisionNormalizedImage source, VisionGeometryEdgeCandidate edge) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source.bytes()));
            if (original == null) {
                throw new AssistantPlanningException("No se pudo decodificar la imagen para crear evidence panel.");
            }
            BufferedImage panel = new BufferedImage(PANEL_WIDTH, PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = panel.createGraphics();
            try {
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawPanel(graphics, original, edge, 0, 0);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(panel, "png", output);
            byte[] bytes = output.toByteArray();
            return new VisionNormalizedImage(
                    "relationship-" + edge.edgeId() + ".png", "image/png", "image/png", bytes,
                    panel.getWidth(), panel.getHeight(), sha256(bytes), true
            );
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudo renderizar el panel local de relacion.", exception);
        }
    }

    public VisionNormalizedImage renderMultiplicityTranscriptionConditioned(
            VisionNormalizedImage source,
            VisionGeometryEdgeCandidate current,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            List<VisionGeometryEdgeCandidate> candidates
    ) {
        try {
            BufferedImage original = decoded(source, "panel condicionado de transcripcion de multiplicidad");
            EndpointData currentEndpoint = endpointData(current, endpoint);
            EndpointCrop crop = endpointCropData(original, currentEndpoint.contactX(), currentEndpoint.contactY());
            BufferedImage conditioned = copy(crop.image());
            for (CompetingEndpoint competitor : competingEndpoints(current, endpointClass.ref(), candidates)) {
                if (maskPixelCount(supportMask(crop, competitor.endpointData(), 90.0, 28.0, 40.0)) > 0) {
                    suppressExclusiveCompetitor(conditioned, crop, currentEndpoint, competitor.endpointData());
                }
            }
            BufferedImage panel = multiplicityPanel(current, endpoint, endpointClass);
            Graphics2D graphics = panel.createGraphics();
            try {
                configure(graphics);
                drawCleanMultiplicityView(
                        graphics, rotateCounterClockwise(conditioned), 4, MULTIPLICITY_HEADER_HEIGHT,
                        panel.getWidth() - 8, panel.getHeight() - MULTIPLICITY_HEADER_HEIGHT - 4
                );
            } finally {
                graphics.dispose();
            }
            return normalizedMultiplicityPanel(panel, current, endpoint, "transcription-conditioned");
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudo renderizar el panel condicionado de transcripcion.", exception);
        }
    }

    List<VisionNormalizedImage> renderMultiplicityTranscriptionMaskDiagnostics(
            VisionNormalizedImage source,
            VisionGeometryEdgeCandidate current,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            List<VisionGeometryEdgeCandidate> candidates
    ) {
        try {
            BufferedImage original = decoded(source, "diagnosticos de mascara de transcripcion");
            EndpointData currentEndpoint = endpointData(current, endpoint);
            EndpointCrop crop = endpointCropData(original, currentEndpoint.contactX(), currentEndpoint.contactY());
            List<VisionNormalizedImage> result = new ArrayList<>();
            result.add(normalizedMultiplicityPanel(copy(crop.image()), current, endpoint, "source-original"));
            boolean[][] currentMask = supportMask(crop, currentEndpoint, 90.0, 36.0, 40.0);
            result.add(normalizedMultiplicityPanel(maskImage(currentMask), current, endpoint, "current-support-mask"));
            for (CompetingEndpoint competitor : competingEndpoints(current, endpointClass.ref(), candidates)) {
                boolean[][] competitorMask = supportMask(crop, competitor.endpointData(), 90.0, 28.0, 40.0);
                if (maskPixelCount(competitorMask) == 0) continue;
                boolean[][] overlap = combine(currentMask, competitorMask, true);
                boolean[][] suppression = suppressMask(currentMask, competitorMask);
                result.add(normalizedMultiplicityPanel(maskImage(competitorMask), current, endpoint,
                        "competitor-" + competitor.edgeId() + "-mask"));
                result.add(normalizedMultiplicityPanel(maskImage(overlap), current, endpoint,
                        "current-competitor-overlap"));
                result.add(normalizedMultiplicityPanel(maskImage(suppression), current, endpoint,
                        "final-suppression-mask"));
                result.add(normalizedMultiplicityPanel(debugOverlay(crop.image(), currentMask, competitorMask, overlap, suppression),
                        current, endpoint, "mask-debug-overlay"));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudieron renderizar diagnosticos de mascara de transcripcion.", exception);
        }
    }

    public VisionNormalizedImage renderMultiplicityOwnership(
            VisionNormalizedImage source,
            VisionGeometryEdgeCandidate currentEdge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            VisionGeometryClassRegion endpointClassRegion,
            List<VisionGeometryEdgeCandidate> allGeometryEdges
    ) {
        try {
            OwnershipRenderingMetadata metadata = ownershipRenderingMetadata(
                    source, currentEdge, endpoint, endpointClass.ref(), endpointClassRegion, allGeometryEdges
            );
            BufferedImage panel = ownershipPanel(currentEdge, endpoint, endpointClass);
            Graphics2D graphics = panel.createGraphics();
            try {
                configure(graphics);
                drawOwnershipGuidedMultiplicityView(graphics, metadata);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                graphics.setColor(Color.DARK_GRAY);
                graphics.drawString("RED = CURRENT   BLUE = OTHER", 6, MULTIPLICITY_HEADER_HEIGHT - 3);
            } finally {
                graphics.dispose();
            }
            return normalizedMultiplicityPanel(panel, currentEdge, endpoint, "ownership");
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudo renderizar el panel de ownership de multiplicidad.", exception);
        }
    }

    public VisionNormalizedImage renderMultiplicityAttribution(
            VisionNormalizedImage source,
            VisionGeometryEdgeCandidate currentEdge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            VisionGeometryClassRegion endpointClassRegion,
            List<VisionGeometryEdgeCandidate> allGeometryEdges,
            String candidateRawLabel
    ) {
        try {
            VisionNormalizedImage context = renderMultiplicityOwnership(
                    source, currentEdge, endpoint, endpointClass, endpointClassRegion, allGeometryEdges
            );
            BufferedImage original = decoded(source, "panel de attribution de multiplicidad");
            EndpointData endpointData = endpointData(currentEdge, endpoint);
            EndpointCrop sourceCrop = endpointCropData(original, endpointData.contactX(), endpointData.contactY());
            BufferedImage contextImage = ImageIO.read(new ByteArrayInputStream(context.bytes()));
            BufferedImage panel = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = panel.createGraphics();
            try {
                configure(graphics);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, panel.getWidth(), panel.getHeight());
                graphics.setColor(Color.BLACK);
                graphics.drawRect(0, 0, panel.getWidth() - 1, panel.getHeight() - 1);
                graphics.drawString("LABEL SOURCE", 6, 18);
                graphics.drawString("candidate=" + quote(candidateRawLabel), 6, 32);
                graphics.drawString("CLASS CONTEXT", 258, 18);
                drawFit(graphics, sourceCrop.image(), 4, 38, 248, 318);
                graphics.drawImage(contextImage, 256, 0, null);
            } finally {
                graphics.dispose();
            }
            return normalizedMultiplicityPanel(panel, currentEdge, endpoint, "attribution");
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudo renderizar el panel de attribution de multiplicidad.", exception);
        }
    }

    OwnershipRenderingMetadata ownershipRenderingMetadata(
            VisionNormalizedImage source,
            VisionGeometryEdgeCandidate current,
            VisionHybridEndpoint endpoint,
            String endpointClassRef,
            VisionGeometryClassRegion endpointClassRegion,
            List<VisionGeometryEdgeCandidate> candidates
    ) {
        try {
            BufferedImage original = decoded(source, "guias de ownership de multiplicidad");
            EndpointData currentEndpoint = endpointData(current, endpoint);
            EndpointCrop crop = ownershipClassCrop(original, endpointClassRegion);
            OwnershipPanelTransform transform = ownershipPanelTransform(crop.image());
            OwnershipEndpointOverlay currentOverlay = endpointOverlay(current.edgeId(), currentEndpoint, crop, transform);
            List<OwnershipEndpointOverlay> competitors = new ArrayList<>();
            for (CompetingEndpoint competitor : competingEndpoints(current, endpointClassRef, candidates)) {
                if (connectorIntersectsCrop(competitor.endpointData(), crop)) {
                    competitors.add(endpointOverlay(competitor.edgeId(), competitor.endpointData(), crop, transform));
                }
            }
            return new OwnershipRenderingMetadata(crop, transform, currentOverlay, List.copyOf(competitors));
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException("No se pudieron obtener las guias de ownership de multiplicidad.", exception);
        }
    }

    List<CompetingEndpoint> competingEndpoints(
            VisionGeometryEdgeCandidate current,
            String endpointClassRef,
            List<VisionGeometryEdgeCandidate> candidates
    ) {
        return candidates.stream()
                .filter(candidate -> !current.edgeId().equals(candidate.edgeId()))
                .map(candidate -> competingEndpoint(candidate, endpointClassRef))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CompetingEndpoint::edgeId))
                .toList();
    }

    private BufferedImage decoded(VisionNormalizedImage source, String purpose) throws Exception {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(source.bytes()));
        if (original == null) {
            throw new AssistantPlanningException("No se pudo decodificar la imagen para crear " + purpose + ".");
        }
        return original;
    }

    private BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private void suppressExclusiveCompetitor(
            BufferedImage image,
            EndpointCrop crop,
            EndpointData current,
            EndpointData competitor
    ) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean inCompetitor = inSupport(x, y, competitor, crop, 90.0, 28.0, 40.0);
                boolean inCurrent = inSupport(x, y, current, crop, 90.0, 36.0, 40.0);
                if (inCompetitor && !inCurrent) {
                    image.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }
    }

    private boolean[][] supportMask(EndpointCrop crop, EndpointData endpoint, double length, double width, double radius) {
        boolean[][] mask = new boolean[crop.image().getHeight()][crop.image().getWidth()];
        for (int y = 0; y < mask.length; y++) for (int x = 0; x < mask[y].length; x++)
            mask[y][x] = inSupport(x, y, endpoint, crop, length, width, radius);
        return mask;
    }

    private boolean[][] combine(boolean[][] left, boolean[][] right, boolean and) {
        boolean[][] result = new boolean[left.length][left[0].length];
        for (int y = 0; y < result.length; y++) for (int x = 0; x < result[y].length; x++)
            result[y][x] = and ? left[y][x] && right[y][x] : left[y][x] || right[y][x];
        return result;
    }

    private boolean[][] suppressMask(boolean[][] current, boolean[][] competitor) {
        boolean[][] result = new boolean[current.length][current[0].length];
        for (int y = 0; y < result.length; y++) for (int x = 0; x < result[y].length; x++)
            result[y][x] = competitor[y][x] && !current[y][x];
        return result;
    }

    private int maskPixelCount(boolean[][] mask) {
        int count = 0;
        for (boolean[] row : mask) for (boolean value : row) if (value) count++;
        return count;
    }

    static int competitorMaskPixelCountForTesting(
            int cropWidth, int cropHeight, int contactX, int contactY, Integer innerX, Integer innerY
    ) {
        VisionRelationshipEvidenceSheetRenderer renderer = new VisionRelationshipEvidenceSheetRenderer();
        BufferedImage canvas = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB);
        return renderer.maskPixelCount(renderer.supportMask(
                new EndpointCrop(canvas, 0, 0), new EndpointData(contactX, contactY, innerX, innerY),
                90.0, 28.0, 40.0
        ));
    }

    private BufferedImage maskImage(boolean[][] mask) {
        BufferedImage image = new BufferedImage(mask[0].length, mask.length, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < mask.length; y++) for (int x = 0; x < mask[y].length; x++)
            image.setRGB(x, y, mask[y][x] ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
        return image;
    }

    private BufferedImage debugOverlay(BufferedImage source, boolean[][] current, boolean[][] competitor, boolean[][] overlap, boolean[][] suppression) {
        BufferedImage image = copy(source);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (overlap[y][x]) image.setRGB(x, y, Color.MAGENTA.getRGB());
            else if (suppression[y][x]) image.setRGB(x, y, new Color(255, 80, 80).getRGB());
            else if (current[y][x]) image.setRGB(x, y, new Color(80, 220, 80).getRGB());
            else if (competitor[y][x]) image.setRGB(x, y, new Color(80, 80, 255).getRGB());
        }
        return image;
    }

    private boolean inSupport(
            double x, double y, EndpointData endpoint, EndpointCrop crop,
            double guideLength, double halfWidth, double contactRadius
    ) {
        double contactX = endpoint.contactX() - crop.x();
        double contactY = endpoint.contactY() - crop.y();
        if (Math.hypot(x - contactX, y - contactY) <= contactRadius) {
            return true;
        }
        if (endpoint.innerX() == null || endpoint.innerY() == null) {
            return false;
        }
        double dx = endpoint.innerX() - endpoint.contactX();
        double dy = endpoint.innerY() - endpoint.contactY();
        double length = Math.hypot(dx, dy);
        if (length < 1e-6) return false;
        double endX = contactX + dx / length * guideLength;
        double endY = contactY + dy / length * guideLength;
        return new Line2D.Double(contactX, contactY, endX, endY).ptSegDist(x, y) <= halfWidth;
    }

    private BufferedImage multiplicityPanel(
            VisionGeometryEdgeCandidate edge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    ) {
        BufferedImage panel = new BufferedImage(SINGLE_MULTIPLICITY_PANEL_WIDTH, MULTIPLICITY_PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = panel.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, panel.getWidth(), panel.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawRect(0, 0, panel.getWidth() - 1, panel.getHeight() - 1);
            graphics.drawString(
                    edge.edgeId() + " · ENDPOINT " + endpoint.name() + " · " + endpointClass.ref()
                            + " " + quote(endpointClass.name()),
                    6, 22
            );
        } finally {
            graphics.dispose();
        }
        return panel;
    }

    private BufferedImage ownershipPanel(
            VisionGeometryEdgeCandidate edge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    ) {
        BufferedImage panel = new BufferedImage(
                MULTIPLICITY_OWNERSHIP_WIDTH, MULTIPLICITY_OWNERSHIP_HEIGHT, BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = panel.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, panel.getWidth(), panel.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawRect(0, 0, panel.getWidth() - 1, panel.getHeight() - 1);
            graphics.drawString(
                    edge.edgeId() + " · ENDPOINT " + endpoint.name() + " · " + endpointClass.ref()
                            + " " + quote(endpointClass.name()),
                    6, 22
            );
        } finally {
            graphics.dispose();
        }
        return panel;
    }

    private CompetingEndpoint competingEndpoint(VisionGeometryEdgeCandidate candidate, String endpointClassRef) {
        if (endpointClassRef.equals(candidate.aClassRef())) {
            return new CompetingEndpoint(candidate.edgeId(), VisionHybridEndpoint.A, endpointData(candidate, VisionHybridEndpoint.A));
        }
        if (endpointClassRef.equals(candidate.bClassRef())) {
            return new CompetingEndpoint(candidate.edgeId(), VisionHybridEndpoint.B, endpointData(candidate, VisionHybridEndpoint.B));
        }
        return null;
    }

    private OwnershipEndpointOverlay endpointOverlay(
            String edgeId,
            EndpointData endpoint,
            EndpointCrop crop,
            OwnershipPanelTransform transform
    ) {
        Point contactCrop = new Point(endpoint.contactX() - crop.x(), endpoint.contactY() - crop.y());
        Point innerCrop = endpoint.innerX() == null || endpoint.innerY() == null ? null
                : new Point(endpoint.innerX() - crop.x(), endpoint.innerY() - crop.y());
        VisionOwnershipPanelPoint directionPanel = endpoint.innerX() == null || endpoint.innerY() == null ? null
                : transform.toPanel(innerCrop.x, innerCrop.y);
        return new OwnershipEndpointOverlay(
                edgeId,
                contactCrop,
                innerCrop,
                transform.toPanel(contactCrop.x, contactCrop.y),
                directionPanel
        );
    }

    private boolean connectorIntersectsCrop(EndpointData endpoint, EndpointCrop crop) {
        double contactX = endpoint.contactX() - crop.x();
        double contactY = endpoint.contactY() - crop.y();
        if (insideCrop(contactX, contactY, crop)) {
            return true;
        }
        if (endpoint.innerX() == null || endpoint.innerY() == null) {
            return false;
        }
        double innerX = endpoint.innerX() - crop.x();
        double innerY = endpoint.innerY() - crop.y();
        return insideCrop(innerX, innerY, crop) || new Line2D.Double(contactX, contactY, innerX, innerY)
                .intersects(0, 0, crop.image().getWidth(), crop.image().getHeight());
    }

    private boolean insideCrop(double x, double y, EndpointCrop crop) {
        return x >= 0 && x < crop.image().getWidth() && y >= 0 && y < crop.image().getHeight();
    }

    private EndpointData endpointData(VisionGeometryEdgeCandidate edge, VisionHybridEndpoint endpoint) {
        return endpoint == VisionHybridEndpoint.A
                ? new EndpointData(edge.contactAX(), edge.contactAY(), edge.innerAX(), edge.innerAY())
                : new EndpointData(edge.contactBX(), edge.contactBY(), edge.innerBX(), edge.innerBY());
    }

    private OwnershipPanelTransform ownershipPanelTransform(BufferedImage crop) {
        int x = 4;
        int y = MULTIPLICITY_HEADER_HEIGHT;
        int width = MULTIPLICITY_OWNERSHIP_WIDTH - 8;
        int height = MULTIPLICITY_OWNERSHIP_HEIGHT - MULTIPLICITY_HEADER_HEIGHT - 4;
        double scale = Math.min(width / (double) crop.getWidth(), height / (double) crop.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(crop.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(crop.getHeight() * scale));
        return new OwnershipPanelTransform(
                x + (width - targetWidth) / 2, y + (height - targetHeight) / 2,
                targetWidth, targetHeight, scale
        );
    }

    private EndpointCrop ownershipClassCrop(BufferedImage source, VisionGeometryClassRegion region) {
        int margin = Math.max(70, Math.min(source.getWidth(), source.getHeight()) / 11);
        int left = Math.max(0, region.x() - margin);
        int top = Math.max(0, region.y() - margin);
        int right = Math.min(source.getWidth(), region.right() + margin);
        int bottom = Math.min(source.getHeight(), region.bottom() + margin);
        return new EndpointCrop(crop(source, left, top, Math.max(1, right - left), Math.max(1, bottom - top)), left, top);
    }

    private VisionNormalizedImage normalizedMultiplicityPanel(
            BufferedImage panel,
            VisionGeometryEdgeCandidate edge,
            VisionHybridEndpoint endpoint,
            String stage
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(panel, "png", output);
        byte[] bytes = output.toByteArray();
        return new VisionNormalizedImage(
                "multiplicity-" + edge.edgeId() + "-" + endpoint.name() + "-" + stage + ".png",
                "image/png", "image/png", bytes, panel.getWidth(), panel.getHeight(), sha256(bytes), true
        );
    }

    private void configure(Graphics2D graphics) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
        return endpointCropData(source, cx, cy).image();
    }

    private EndpointCrop endpointCropData(BufferedImage source, int cx, int cy) {
        int radius = Math.max(70, Math.min(source.getWidth(), source.getHeight()) / 11);
        int x = Math.max(0, cx - radius);
        int y = Math.max(0, cy - radius);
        int right = Math.min(source.getWidth(), cx + radius);
        int bottom = Math.min(source.getHeight(), cy + radius);
        return new EndpointCrop(crop(source, x, y, Math.max(1, right - x), Math.max(1, bottom - y)), x, y);
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

    private void drawCleanMultiplicityView(
            Graphics2D graphics,
            BufferedImage image,
            int x,
            int y,
            int width,
            int height
    ) {
        drawFit(graphics, image, x, y, width, height);
    }

    private void drawOwnershipGuidedMultiplicityView(Graphics2D graphics, OwnershipRenderingMetadata metadata) {
        EndpointCrop crop = metadata.crop();
        OwnershipPanelTransform transform = metadata.transform();
        graphics.setColor(new Color(245, 245, 245));
        graphics.fillRect(transform.x(), transform.y(), transform.targetWidth(), transform.targetHeight());
        graphics.drawImage(
                crop.image(), transform.x(), transform.y(), transform.targetWidth(), transform.targetHeight(), null
        );
        ConnectorCorridor corridor = metadata.current().innerCrop() == null ? null : connectorCorridor(
                metadata.current().contactCrop(), metadata.current().innerCrop(),
                crop.image().getWidth(), crop.image().getHeight()
        );
        if (corridor != null) {
            Area highlighted = new Area(highlightedCorridor(corridor, transform.x(), transform.y(), transform.scale()));
            for (OwnershipEndpointOverlay competitor : metadata.competitors()) {
                double radius = COMPETITOR_CONTACT_ZONE_RADIUS * transform.scale();
                highlighted.add(new Area(new Ellipse2D.Double(
                        competitor.contactPanel().x() - radius, competitor.contactPanel().y() - radius,
                        radius * 2, radius * 2
                )));
            }
            Composite previousComposite = graphics.getComposite();
            Shape previousClip = graphics.getClip();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
            graphics.setColor(Color.WHITE);
            graphics.fillRect(transform.x(), transform.y(), transform.targetWidth(), transform.targetHeight());
            graphics.setComposite(previousComposite);
            graphics.clip(highlighted);
            graphics.drawImage(
                    crop.image(), transform.x(), transform.y(), transform.targetWidth(), transform.targetHeight(), null
            );
            graphics.setClip(previousClip);
        }

        Shape previousClip = graphics.getClip();
        graphics.clipRect(transform.x(), transform.y(), transform.targetWidth(), transform.targetHeight());
        for (OwnershipEndpointOverlay competitor : metadata.competitors()) {
            drawConnectorOverlay(graphics, competitor, Color.BLUE, 5);
        }
        drawConnectorOverlay(graphics, metadata.current(), Color.RED, 6);
        graphics.setClip(previousClip);
    }

    private void drawConnectorOverlay(
            Graphics2D graphics,
            OwnershipEndpointOverlay overlay,
            Color color,
            int markerRadius
    ) {
        int markerX = (int) Math.round(overlay.contactPanel().x());
        int markerY = (int) Math.round(overlay.contactPanel().y());
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(color == Color.BLUE ? 2f : 1.25f));
        graphics.drawOval(markerX - markerRadius, markerY - markerRadius, markerRadius * 2, markerRadius * 2);
        graphics.drawLine(markerX - 4, markerY, markerX + 4, markerY);
        graphics.drawLine(markerX, markerY - 4, markerX, markerY + 4);
        if (overlay.directionPanel() != null) {
            drawArrow(
                    graphics, markerX, markerY,
                    (int) Math.round(overlay.directionPanel().x()),
                    (int) Math.round(overlay.directionPanel().y())
            );
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        graphics.drawString(overlay.edgeId(), markerX + markerRadius + 3, markerY - markerRadius - 2);
    }

    static ConnectorCorridor connectorCorridor(Point contact, Point inner, int cropWidth, int cropHeight) {
        if (contact == null || inner == null || cropWidth <= 0 || cropHeight <= 0) {
            return null;
        }
        double dx = inner.x - contact.x;
        double dy = inner.y - contact.y;
        double distance = Math.hypot(dx, dy);
        if (distance < 1e-6) {
            return null;
        }
        double unitX = dx / distance;
        double unitY = dy / distance;
        double horizontal = unitX > 0 ? (cropWidth - 1 - contact.x) / unitX
                : unitX < 0 ? contact.x / -unitX : Double.POSITIVE_INFINITY;
        double vertical = unitY > 0 ? (cropHeight - 1 - contact.y) / unitY
                : unitY < 0 ? contact.y / -unitY : Double.POSITIVE_INFINITY;
        double length = Math.min(CORRIDOR_MAX_LENGTH, Math.min(horizontal, vertical));
        if (length <= 0.0) {
            return null;
        }
        return new ConnectorCorridor(
                contact.x, contact.y, contact.x + unitX * length, contact.y + unitY * length,
                CORRIDOR_HALF_WIDTH, CONTACT_ZONE_RADIUS
        );
    }

    private Shape highlightedCorridor(ConnectorCorridor corridor, int dx, int dy, double scale) {
        double startX = dx + corridor.startX() * scale;
        double startY = dy + corridor.startY() * scale;
        double endX = dx + corridor.endX() * scale;
        double endY = dy + corridor.endY() * scale;
        Area result = new Area(new BasicStroke(
                (float) (corridor.halfWidth() * 2 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        ).createStrokedShape(new Line2D.Double(startX, startY, endX, endY)));
        double radius = corridor.contactRadius() * scale;
        result.add(new Area(new Ellipse2D.Double(startX - radius, startY - radius, radius * 2, radius * 2)));
        return result;
    }

    private BufferedImage rotateCounterClockwise(BufferedImage source) {
        BufferedImage rotated = new BufferedImage(source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                rotated.setRGB(y, source.getWidth() - 1 - x, source.getRGB(x, y));
            }
        }
        return rotated;
    }

    private void drawArrow(Graphics2D graphics, int x1, int y1, int x2, int y2) {
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int head = 6;
        graphics.drawLine(x2, y2,
                x2 - (int) Math.round(head * Math.cos(angle - Math.PI / 6)),
                y2 - (int) Math.round(head * Math.sin(angle - Math.PI / 6)));
        graphics.drawLine(x2, y2,
                x2 - (int) Math.round(head * Math.cos(angle + Math.PI / 6)),
                y2 - (int) Math.round(head * Math.sin(angle + Math.PI / 6)));
    }

    private String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "'")) + "\"";
    }

    record EndpointCrop(BufferedImage image, int x, int y) {
    }

    record EndpointData(int contactX, int contactY, Integer innerX, Integer innerY) {
    }

    record OwnershipPanelTransform(int x, int y, int targetWidth, int targetHeight, double scale) {
        VisionOwnershipPanelPoint toPanel(int cropX, int cropY) {
            return new VisionOwnershipPanelPoint(x + cropX * scale, y + cropY * scale);
        }
    }

    record CompetingEndpoint(String edgeId, VisionHybridEndpoint endpoint, EndpointData endpointData) {
    }

    record OwnershipEndpointOverlay(
            String edgeId,
            Point contactCrop,
            Point innerCrop,
            VisionOwnershipPanelPoint contactPanel,
            VisionOwnershipPanelPoint directionPanel
    ) {
    }

    record OwnershipRenderingMetadata(
            EndpointCrop crop,
            OwnershipPanelTransform transform,
            OwnershipEndpointOverlay current,
            List<OwnershipEndpointOverlay> competitors
    ) {
    }

    record ConnectorCorridor(
            double startX,
            double startY,
            double endX,
            double endY,
            double halfWidth,
            double contactRadius
    ) {
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
