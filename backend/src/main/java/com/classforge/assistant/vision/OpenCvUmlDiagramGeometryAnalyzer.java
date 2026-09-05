package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

@Component
public class OpenCvUmlDiagramGeometryAnalyzer implements UmlDiagramGeometryAnalyzer {

    private static volatile boolean loaded;

    /** Compatibility helper for deterministic contract tests from CAL-010. */
    public UmlDiagramGeometry analyze(
            VisionNormalizedImage input,
            VisionClassLocalizationProposal localization
    ) {
        List<VisionGeometryClassRegion> mapped = new ArrayList<>();
        int index = 1;
        for (VisionClassLocalization item : localization.safeMappings()) {
            mapped.add(new VisionGeometryClassRegion(
                    "B" + index++, item.classRef(), item.x(), item.y(),
                    item.width(), item.height(), item.confidence()
            ));
        }
        return analyze(input, mapped);
    }

    @Override
    public UmlDiagramGeometry analyze(
            VisionNormalizedImage input,
            List<VisionGeometryClassRegion> regions
    ) {
        ensureLoaded();
        MatOfByte encoded = new MatOfByte(input.bytes());
        Mat image;
        try {
            image = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
        } finally {
            encoded.release();
        }
        if (image.empty()) {
            image.release();
            throw new AssistantPlanningException("OpenCV no pudo decodificar la imagen visual normalizada.");
        }

        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();
        Mat closed = new Mat();
        Mat lines = new Mat();
        Mat segmentOverlay = new Mat();
        Mat overlay = new Mat();
        List<Mat> kernels = new ArrayList<>();
        try {
            if (regions == null || regions.isEmpty()) {
                throw new AssistantPlanningException("OpenCV requiere cajas UML mapeadas antes de reconstruir relaciones.");
            }
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);
            Imgproc.adaptiveThreshold(
                    blurred,
                    binary,
                    255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    31,
                    9
            );

            int minDimension = Math.max(64, Math.min(image.cols(), image.rows()));
            maskClassRegions(binary, regions, image.cols(), image.rows(), minDimension);

            directionalClose(binary, closed, minDimension, kernels);

            Imgproc.HoughLinesP(
                    closed,
                    lines,
                    1,
                    Math.PI / 180.0,
                    Math.max(18, minDimension / 45),
                    Math.max(24, minDimension / 28),
                    Math.max(10, minDimension / 90)
            );

            List<Segment> segments = segments(lines);
            GeometryDiagnostics diagnostics = new GeometryDiagnostics(input.originalFilename(), segments);
            GeometryGraph graph = graph(segments, regions, minDimension, closed, diagnostics);
            List<VisionGeometryEdgeCandidate> candidates = candidates(
                    regions,
                    graph,
                    segments,
                    image.cols(),
                    image.rows(),
                    diagnostics
            );

            segmentOverlay = image.clone();
            for (Segment segment : segments) {
                Imgproc.line(segmentOverlay, segment.a(), segment.b(), new Scalar(0, 180, 0), 2);
            }
            overlay = image.clone();
            for (VisionGeometryClassRegion region : regions) {
                Imgproc.rectangle(
                        overlay,
                        new Point(region.x(), region.y()),
                        new Point(region.right(), region.bottom()),
                        new Scalar(0, 0, 255),
                        2
                );
                Imgproc.putText(
                        overlay,
                        region.geometryId() + ":" + region.classRef(),
                        new Point(region.x() + 4, Math.max(18, region.y() + 20)),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        new Scalar(255, 0, 0),
                        2
                );
            }
            for (VisionGeometryEdgeCandidate edge : candidates) {
                Imgproc.line(
                        overlay,
                        new Point(edge.contactAX(), edge.contactAY()),
                        new Point(edge.contactBX(), edge.contactBY()),
                        new Scalar(180, 0, 180),
                        2
                );
                Imgproc.putText(
                        overlay,
                        edge.edgeId(),
                        new Point(
                                (edge.contactAX() + edge.contactBX()) / 2.0,
                                (edge.contactAY() + edge.contactBY()) / 2.0
                        ),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.45,
                        new Scalar(180, 0, 180),
                        1
                );
            }

            diagnostics.write(candidates);
            return new UmlDiagramGeometry(
                    regions,
                    candidates,
                    png(binary),
                    png(segmentOverlay),
                    png(overlay)
            );
        } finally {
            for (Mat kernel : kernels) {
                kernel.release();
            }
            gray.release();
            blurred.release();
            binary.release();
            closed.release();
            lines.release();
            segmentOverlay.release();
            overlay.release();
            image.release();
        }
    }

    private void maskClassRegions(
            Mat binary,
            List<VisionGeometryClassRegion> regions,
            int imageWidth,
            int imageHeight,
            int minDimension
    ) {
        // FlowExtract-style node suppression: remove the full class rectangle, including
        // its border, so connectors touching the same class cannot become connected
        // through the class outline itself. A small pad also absorbs hand-drawn borders.
        int pad = Math.max(2, minDimension / 350);
        for (VisionGeometryClassRegion region : regions) {
            int x1 = clamp(region.x() - pad, 0, imageWidth - 1);
            int y1 = clamp(region.y() - pad, 0, imageHeight - 1);
            int x2 = clamp(region.right() + pad, 0, imageWidth - 1);
            int y2 = clamp(region.bottom() + pad, 0, imageHeight - 1);
            if (x2 > x1 && y2 > y1) {
                Imgproc.rectangle(
                        binary,
                        new Point(x1, y1),
                        new Point(x2, y2),
                        Scalar.all(0),
                        Imgproc.FILLED
                );
            }
        }
    }

    private void directionalClose(Mat binary, Mat destination, int minDimension, List<Mat> kernels) {
        int length = Math.max(3, minDimension / 150);
        if (length % 2 == 0) {
            length++;
        }
        length = Math.min(length, 11);

        Mat horizontal = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(length, 1));
        Mat vertical = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, length));
        Mat diagonalDown = diagonalKernel(length, false);
        Mat diagonalUp = diagonalKernel(length, true);
        kernels.add(horizontal);
        kernels.add(vertical);
        kernels.add(diagonalDown);
        kernels.add(diagonalUp);

        binary.copyTo(destination);
        for (Mat kernel : kernels) {
            Mat repaired = new Mat();
            try {
                Imgproc.morphologyEx(binary, repaired, Imgproc.MORPH_CLOSE, kernel);
                Core.bitwise_or(destination, repaired, destination);
            } finally {
                repaired.release();
            }
        }
    }

    private Mat diagonalKernel(int size, boolean rising) {
        Mat kernel = Mat.zeros(size, size, CvType.CV_8U);
        for (int i = 0; i < size; i++) {
            int col = rising ? size - 1 - i : i;
            kernel.put(i, col, 1);
        }
        return kernel;
    }

    private List<Segment> segments(Mat lines) {
        List<Segment> result = new ArrayList<>();
        for (int row = 0; row < lines.rows(); row++) {
            double[] values = lines.get(row, 0);
            if (values == null || values.length < 4) {
                continue;
            }
            Point a = new Point(values[0], values[1]);
            Point b = new Point(values[2], values[3]);
            double length = Math.hypot(a.x - b.x, a.y - b.y);
            if (length >= 12) {
                result.add(new Segment(a, b, length));
            }
        }
        return result;
    }

    private GeometryGraph graph(
            List<Segment> segments,
            List<VisionGeometryClassRegion> regions,
            int minDimension,
            Mat closed,
            GeometryDiagnostics diagnostics
    ) {
        int endpointCount = segments.size() * 2;
        Dsu dsu = new Dsu(endpointCount);
        List<Point> points = new ArrayList<>(endpointCount);
        for (int i = 0; i < segments.size(); i++) {
            points.add(segments.get(i).a());
            points.add(segments.get(i).b());
            dsu.union(i * 2, i * 2 + 1);
        }

        double joinDistance = Math.max(6.0, minDimension / 110.0);
        double contactDistance = Math.max(10.0, minDimension / 75.0);
        Map<Integer, Set<String>> directTouches = endpointTouches(dsu, points, regions, contactDistance, null);
        double joinSquared = joinDistance * joinDistance;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            for (int j = i + 1; j < points.size(); j++) {
                Point b = points.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                if (dx * dx + dy * dy <= joinSquared) {
                    unionWithDiagnostics(dsu, i, j, "normal-endpoint-proximity", directTouches, diagnostics);
                }
            }
        }

        // ReSECDI-style polygonal/segment merging: Hough often returns several
        // overlapping or slightly broken fragments for one hand-drawn connector.
        // Merge only near-collinear fragments; geometric crossings at a different
        // angle are deliberately NOT junctions.
        for (int i = 0; i < segments.size(); i++) {
            for (int j = i + 1; j < segments.size(); j++) {
                if (collinearConnected(segments.get(i), segments.get(j), joinDistance * 1.5)) {
                    unionWithDiagnostics(dsu, i * 2, j * 2, "collinear-fragment", directTouches, diagnostics);
                }
            }
        }

        diagnostics.componentsBefore(dsu, segments, points, directTouches);

        double bridgeDistance = Math.max(joinDistance, minDimension / 60.0);
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                int rootA = dsu.find(i);
                int rootB = dsu.find(j);
                if (rootA == rootB) {
                    continue;
                }
                Point a = points.get(i);
                Point b = points.get(j);
                RasterEndpointBridge bridge = evaluateRasterEndpointBridge(closed, a, b, bridgeDistance);
                if (bridge.distance() > bridgeDistance) {
                    continue;
                }
                diagnostics.endpointBridge(
                        rootA, rootB, a, b, bridgeDistance, bridge,
                        directClasses(a, regions, contactDistance), directClasses(b, regions, contactDistance),
                        pointClassDistances(a, regions), pointClassDistances(b, regions)
                );
                if (bridge.accepted()) {
                    unionWithDiagnostics(dsu, i, j, "raster-supported-endpoint-bridge", directTouches, diagnostics);
                }
            }
        }

        Map<Integer, Set<String>> directTouched = endpointTouches(dsu, points, regions, contactDistance, null);
        Map<Integer, Set<String>> localTouched = new HashMap<>();
        Map<Integer, Map<String, Point>> contacts = endpointContacts(dsu, points, regions, contactDistance);
        propagateLocalRasterAttachments(
                dsu, points, regions, closed, minDimension, contactDistance,
                directTouched, localTouched, contacts, diagnostics
        );
        Map<Integer, Set<String>> effectiveTouched = effectiveTouches(directTouched, localTouched);
        Map<Integer, Double> lengths = new HashMap<>();

        for (int i = 0; i < segments.size(); i++) {
            int root = dsu.find(i * 2);
            lengths.merge(root, segments.get(i).length(), Double::sum);
        }
        Map<Integer, List<Integer>> segmentIds = segmentIdsByRoot(dsu, segments.size());
        diagnostics.componentsAfter(segmentIds, directTouched, localTouched, effectiveTouched, contacts);
        diagnostics.finalPairDecisions(effectiveTouched);
        return new GeometryGraph(effectiveTouched, contacts, lengths, segmentIds);
    }

    private void unionWithDiagnostics(
            Dsu dsu,
            int a,
            int b,
            String reason,
            Map<Integer, Set<String>> directTouches,
            GeometryDiagnostics diagnostics
    ) {
        int rootA = dsu.find(a);
        int rootB = dsu.find(b);
        if (rootA == rootB) {
            return;
        }
        Set<String> touchesA = Set.copyOf(directTouches.getOrDefault(rootA, Set.of()));
        Set<String> touchesB = Set.copyOf(directTouches.getOrDefault(rootB, Set.of()));
        int root = dsu.unionAndReturnRoot(a, b);
        Set<String> merged = new LinkedHashSet<>(touchesA);
        merged.addAll(touchesB);
        directTouches.remove(rootA);
        directTouches.remove(rootB);
        if (!merged.isEmpty()) {
            directTouches.put(root, merged);
        }
        diagnostics.unionEvent(reason, rootA, rootB, root, touchesA, touchesB, merged);
    }

    private List<String> directClasses(Point point, List<VisionGeometryClassRegion> regions, double contactDistance) {
        return regions.stream().filter(region -> nearBorder(point, region, contactDistance))
                .map(VisionGeometryClassRegion::geometryId).toList();
    }

    private List<RasterDistance> pointClassDistances(Point point, List<VisionGeometryClassRegion> regions) {
        return regions.stream().map(region -> new RasterDistance(region, distanceToBorder(point, region), point)).toList();
    }

    private Map<Integer, Set<String>> endpointTouches(
            Dsu dsu,
            List<Point> points,
            List<VisionGeometryClassRegion> regions,
            double contactDistance,
            Map<Integer, Map<String, Point>> contacts
    ) {
        Map<Integer, Set<String>> touched = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            int root = dsu.find(i);
            Point point = points.get(i);
            for (VisionGeometryClassRegion region : regions) {
                if (!nearBorder(point, region, contactDistance)) {
                    continue;
                }
                touched.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(region.geometryId());
                if (contacts != null) {
                    Map<String, Point> componentContacts = contacts.computeIfAbsent(root, ignored -> new LinkedHashMap<>());
                    Point previous = componentContacts.get(region.geometryId());
                    if (previous == null || distanceToBorder(point, region) < distanceToBorder(previous, region)) {
                        componentContacts.put(region.geometryId(), point);
                    }
                }
            }
        }
        return touched;
    }

    private Map<Integer, Map<String, Point>> endpointContacts(
            Dsu dsu,
            List<Point> points,
            List<VisionGeometryClassRegion> regions,
            double contactDistance
    ) {
        Map<Integer, Map<String, Point>> contacts = new HashMap<>();
        endpointTouches(dsu, points, regions, contactDistance, contacts);
        return contacts;
    }

    private Map<Integer, Set<String>> effectiveTouches(
            Map<Integer, Set<String>> directTouched,
            Map<Integer, Set<String>> localTouched
    ) {
        Map<Integer, Set<String>> result = new HashMap<>();
        Set<Integer> roots = new LinkedHashSet<>();
        roots.addAll(directTouched.keySet());
        roots.addAll(localTouched.keySet());
        for (int root : roots) {
            Set<String> effective = new LinkedHashSet<>(directTouched.getOrDefault(root, Set.of()));
            effective.addAll(localTouched.getOrDefault(root, Set.of()));
            if (!effective.isEmpty()) {
                result.put(root, effective);
            }
        }
        return result;
    }

    RasterEndpointBridge evaluateRasterEndpointBridge(Mat closed, Point a, Point b, double bridgeDistance) {
        double distance = Math.hypot(a.x - b.x, a.y - b.y);
        if (distance > bridgeDistance) {
            return new RasterEndpointBridge(distance, 0.0, false, "bridge-distance-exceeded");
        }
        double coverage = rasterBridgeCoverage(closed, a, b, 2);
        return new RasterEndpointBridge(distance, coverage, coverage >= 0.75,
                coverage >= 0.75 ? null : "insufficient-raster-coverage");
    }

    private double rasterBridgeCoverage(Mat closed, Point a, Point b, int tubeRadius) {
        int samples = Math.max(2, (int) Math.ceil(Math.hypot(a.x - b.x, a.y - b.y)) + 1);
        int foreground = 0;
        for (int index = 0; index < samples; index++) {
            double fraction = index / (double) (samples - 1);
            double x = a.x + (b.x - a.x) * fraction;
            double y = a.y + (b.y - a.y) * fraction;
            if (hasForegroundInTube(closed, x, y, tubeRadius)) {
                foreground++;
            }
        }
        return foreground / (double) samples;
    }

    private boolean hasForegroundInTube(Mat closed, double x, double y, int radius) {
        int centerX = (int) Math.round(x);
        int centerY = (int) Math.round(y);
        for (int yOffset = -radius; yOffset <= radius; yOffset++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                int sampleX = centerX + xOffset;
                int sampleY = centerY + yOffset;
                if (sampleX >= 0 && sampleX < closed.cols() && sampleY >= 0 && sampleY < closed.rows()) {
                    double[] value = closed.get(sampleY, sampleX);
                    if (value != null && value.length > 0 && value[0] > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private LocalRasterAttachment localRasterAttachment(
            Mat closed,
            Point endpoint,
            int radius,
            List<VisionGeometryClassRegion> regions,
            double contactDistance
    ) {
        int left = clamp((int) Math.round(endpoint.x) - radius, 0, closed.cols() - 1);
        int top = clamp((int) Math.round(endpoint.y) - radius, 0, closed.rows() - 1);
        int right = clamp((int) Math.round(endpoint.x) + radius, 0, closed.cols() - 1);
        int bottom = clamp((int) Math.round(endpoint.y) + radius, 0, closed.rows() - 1);
        LocalRoi roi = new LocalRoi(left, top, right - left + 1, bottom - top + 1);
        boolean[][] visited = new boolean[roi.height()][roi.width()];
        ArrayDeque<Point> pending = new ArrayDeque<>();
        int endpointX = (int) Math.round(endpoint.x);
        int endpointY = (int) Math.round(endpoint.y);
        for (int y = Math.max(roi.y(), endpointY - 2); y <= Math.min(roi.bottom(), endpointY + 2); y++) {
            for (int x = Math.max(roi.x(), endpointX - 2); x <= Math.min(roi.right(), endpointX + 2); x++) {
                if (foreground(closed, x, y) && !visited[y - roi.y()][x - roi.x()]) {
                    visited[y - roi.y()][x - roi.x()] = true;
                    pending.add(new Point(x, y));
                }
            }
        }
        if (pending.isEmpty()) {
            return new LocalRasterAttachment(roi, false, List.of(), List.of(), contactDistance,
                    "NO_LOCAL_RASTER_SUPPORT", null, null);
        }
        List<Point> pixels = new ArrayList<>();
        while (!pending.isEmpty()) {
            Point pixel = pending.removeFirst();
            pixels.add(pixel);
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    if (xOffset == 0 && yOffset == 0) {
                        continue;
                    }
                    int x = (int) pixel.x + xOffset;
                    int y = (int) pixel.y + yOffset;
                    if (x < roi.x() || x > roi.right() || y < roi.y() || y > roi.bottom()
                            || visited[y - roi.y()][x - roi.x()] || !foreground(closed, x, y)) {
                        continue;
                    }
                    visited[y - roi.y()][x - roi.x()] = true;
                    pending.add(new Point(x, y));
                }
            }
        }
        List<RasterDistance> distances = new ArrayList<>();
        for (VisionGeometryClassRegion region : regions) {
            Point closest = null;
            double distance = Double.MAX_VALUE;
            for (Point pixel : pixels) {
                double candidate = distanceToBorder(pixel, region);
                if (candidate < distance) {
                    distance = candidate;
                    closest = pixel;
                }
            }
            distances.add(new RasterDistance(region, distance, closest));
        }
        List<RasterDistance> candidates = distances.stream()
                .filter(distance -> distance.distance() <= contactDistance)
                .toList();
        if (candidates.isEmpty()) {
            return new LocalRasterAttachment(roi, true, pixels, distances, contactDistance,
                    "NO_LOCAL_CLASS_CONTACT", null, null);
        }
        if (candidates.size() > 1) {
            return new LocalRasterAttachment(roi, true, pixels, distances, contactDistance,
                    "AMBIGUOUS_LOCAL_CLASS_CONTACT", null, null);
        }
        RasterDistance accepted = candidates.getFirst();
        return new LocalRasterAttachment(roi, true, pixels, distances, contactDistance,
                "ACCEPTED", accepted.region(), accepted.point());
    }

    private boolean foreground(Mat closed, int x, int y) {
        double[] value = closed.get(y, x);
        return value != null && value.length > 0 && value[0] > 0;
    }

    private void propagateLocalRasterAttachments(
            Dsu dsu,
            List<Point> points,
            List<VisionGeometryClassRegion> regions,
            Mat closed,
            int minDimension,
            double contactDistance,
            Map<Integer, Set<String>> directTouched,
            Map<Integer, Set<String>> localTouched,
            Map<Integer, Map<String, Point>> contacts,
            GeometryDiagnostics diagnostics
    ) {
        int radius = Math.max(24, minDimension / 12);
        for (int index = 0; index < points.size(); index++) {
            int root = dsu.find(index);
            Point endpoint = points.get(index);
            LocalRasterAttachment attachment = localRasterAttachment(
                    closed, endpoint, radius, regions, contactDistance
            );
            Set<String> directBefore = Set.copyOf(directTouched.getOrDefault(root, Set.of()));
            Set<String> localBefore = Set.copyOf(localTouched.getOrDefault(root, Set.of()));
            LocalAttachmentDecision decision = attachment.acceptedClass() == null
                    ? LocalAttachmentDecision.noCandidate(attachment.result(), directBefore, localBefore)
                    : evaluateLocalAttachment(directBefore, localBefore, attachment.acceptedClass().geometryId());
            if (decision.accepted() && attachment.acceptedClass() != null
                    && !directBefore.contains(attachment.acceptedClass().geometryId())) {
                localTouched.computeIfAbsent(root, ignored -> new LinkedHashSet<>())
                        .add(attachment.acceptedClass().geometryId());
                contacts.computeIfAbsent(root, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(attachment.acceptedClass().geometryId(), attachment.contactPoint());
            }
            Set<String> effectiveAfter = new LinkedHashSet<>(directTouched.getOrDefault(root, Set.of()));
            effectiveAfter.addAll(localTouched.getOrDefault(root, Set.of()));
            diagnostics.localRasterAttachment(root, endpoint, attachment, directBefore, localBefore, decision, effectiveAfter);
        }
    }

    LocalAttachmentDecision evaluateLocalAttachment(Set<String> directTouched, Set<String> localTouched, String candidateClass) {
        Set<String> effective = new LinkedHashSet<>(directTouched);
        effective.addAll(localTouched);
        if (candidateClass == null) {
            return LocalAttachmentDecision.noCandidate("NO_LOCAL_CLASS_CONTACT", directTouched, localTouched);
        }
        Set<String> prospective = new LinkedHashSet<>(effective);
        prospective.add(candidateClass);
        if (effective.contains(candidateClass)) {
            return new LocalAttachmentDecision(candidateClass, prospective, "REINFORCEMENT", true);
        }
        if (directTouched.size() >= 2) {
            return new LocalAttachmentDecision(candidateClass, prospective, "DIRECT_PAIR_ALREADY_COMPLETE", false);
        }
        if (prospective.size() > 2) {
            return new LocalAttachmentDecision(candidateClass, prospective, "COMPONENT_CLASS_CARDINALITY_GUARD", false);
        }
        return new LocalAttachmentDecision(candidateClass, prospective, "ACCEPTED", true);
    }

    private boolean collinearConnected(Segment a, Segment b, double tolerance) {
        double angleA = Math.atan2(a.b().y - a.a().y, a.b().x - a.a().x);
        double angleB = Math.atan2(b.b().y - b.a().y, b.b().x - b.a().x);
        double delta = Math.abs(angleA - angleB);
        delta = Math.min(delta, Math.PI - delta);
        if (delta > Math.toRadians(12.0)) {
            return false;
        }

        double distance = Math.min(
                Math.min(pointToSegmentDistance(a.a(), b), pointToSegmentDistance(a.b(), b)),
                Math.min(pointToSegmentDistance(b.a(), a), pointToSegmentDistance(b.b(), a))
        );
        return distance <= tolerance;
    }

    private double pointToSegmentDistance(Point p, Segment segment) {
        double vx = segment.b().x - segment.a().x;
        double vy = segment.b().y - segment.a().y;
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared <= 1e-9) {
            return Math.hypot(p.x - segment.a().x, p.y - segment.a().y);
        }
        double t = ((p.x - segment.a().x) * vx + (p.y - segment.a().y) * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = segment.a().x + t * vx;
        double y = segment.a().y + t * vy;
        return Math.hypot(p.x - x, p.y - y);
    }

    private List<VisionGeometryEdgeCandidate> candidates(
            List<VisionGeometryClassRegion> regions,
            GeometryGraph graph,
            List<Segment> segments,
            int imageWidth,
            int imageHeight,
            GeometryDiagnostics diagnostics
    ) {
        Map<String, VisionGeometryClassRegion> byId = new LinkedHashMap<>();
        for (VisionGeometryClassRegion region : regions) {
            byId.put(region.geometryId(), region);
        }

        List<CandidateDraft> drafts = new ArrayList<>();
        for (Map.Entry<Integer, Set<String>> entry : graph.touched().entrySet()) {
            // Plain UML binary relations should connect exactly two class regions.
            // Components touching 0/1 regions are incomplete; >2 are ambiguous
            // (usually a crossing or segmentation artefact) and are fail-closed.
            if (entry.getValue().size() != 2) {
                continue;
            }
            List<String> ids = new ArrayList<>(entry.getValue());
            VisionGeometryClassRegion a = byId.get(ids.get(0));
            VisionGeometryClassRegion b = byId.get(ids.get(1));
            if (a == null || b == null) {
                continue;
            }
            Map<String, Point> contacts = graph.contacts().getOrDefault(entry.getKey(), Map.of());
            Point contactA = contacts.get(a.geometryId());
            Point contactB = contacts.get(b.geometryId());
            if (contactA == null || contactB == null) {
                continue;
            }

            double rawScore = graph.lengths().getOrDefault(entry.getKey(), 0.0);
            if (rawScore <= 0) {
                continue;
            }
            double normalized = Math.min(
                    1.0,
                    rawScore / Math.max(1.0, Math.hypot(imageWidth, imageHeight))
            );
            int margin = Math.max(24, Math.min(imageWidth, imageHeight) / 35);
            int x1 = clamp(Math.min(a.x(), b.x()) - margin, 0, imageWidth - 1);
            int y1 = clamp(Math.min(a.y(), b.y()) - margin, 0, imageHeight - 1);
            int x2 = clamp(Math.max(a.right(), b.right()) + margin, 1, imageWidth);
            int y2 = clamp(Math.max(a.bottom(), b.bottom()) + margin, 1, imageHeight);
            drafts.add(new CandidateDraft(
                    a, b, normalized,
                    x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1),
                    contactA, contactB,
                    innerPoint(contactA, graph.segmentIds().getOrDefault(entry.getKey(), List.of()), segments),
                    innerPoint(contactB, graph.segmentIds().getOrDefault(entry.getKey(), List.of()), segments)
            ));
            diagnostics.edgeCandidate(pairKey(a.geometryId(), b.geometryId()), entry.getKey(), normalized);
        }

        drafts.sort(Comparator
                .comparingDouble(CandidateDraft::score).reversed()
                .thenComparing(draft -> draft.a().geometryId())
                .thenComparing(draft -> draft.b().geometryId()));

        List<VisionGeometryEdgeCandidate> result = new ArrayList<>();
        Set<String> seenPairs = new LinkedHashSet<>();
        int edge = 1;
        for (CandidateDraft draft : drafts) {
            String pair = pairKey(draft.a().geometryId(), draft.b().geometryId());
            if (!seenPairs.add(pair)) {
                continue;
            }
            result.add(new VisionGeometryEdgeCandidate(
                    "E" + edge++,
                    draft.a().geometryId(), draft.b().geometryId(),
                    draft.a().classRef(), draft.b().classRef(),
                    draft.score(),
                    draft.cropX(), draft.cropY(), draft.cropWidth(), draft.cropHeight(),
                    (int) Math.round(draft.contactA().x), (int) Math.round(draft.contactA().y),
                    (int) Math.round(draft.contactB().x), (int) Math.round(draft.contactB().y),
                    roundedX(draft.innerA()), roundedY(draft.innerA()),
                    roundedX(draft.innerB()), roundedY(draft.innerB())
            ));
        }
        return List.copyOf(result);
    }

    private Point innerPoint(Point contact, List<Integer> segmentIds, List<Segment> segments) {
        Segment closest = null;
        Point nearest = null;
        double distance = Double.MAX_VALUE;
        for (Integer segmentId : segmentIds) {
            if (segmentId == null || segmentId < 0 || segmentId >= segments.size()) {
                continue;
            }
            Segment segment = segments.get(segmentId);
            Point projection = projection(contact, segment);
            double candidateDistance = Math.hypot(contact.x - projection.x, contact.y - projection.y);
            if (candidateDistance < distance) {
                closest = segment;
                nearest = projection;
                distance = candidateDistance;
            }
        }
        if (closest == null || nearest == null) {
            return null;
        }
        Point target = distance > 1.0 ? nearest
                : fartherEndpoint(contact, closest);
        double dx = target.x - contact.x;
        double dy = target.y - contact.y;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            return null;
        }
        double scale = Math.min(28.0, length) / length;
        return new Point(contact.x + dx * scale, contact.y + dy * scale);
    }

    private Point projection(Point point, Segment segment) {
        double dx = segment.b().x - segment.a().x;
        double dy = segment.b().y - segment.a().y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-9) {
            return segment.a();
        }
        double t = ((point.x - segment.a().x) * dx + (point.y - segment.a().y) * dy) / lengthSquared;
        t = clampDouble(t, 0.0, 1.0);
        return new Point(segment.a().x + t * dx, segment.a().y + t * dy);
    }

    private Point fartherEndpoint(Point contact, Segment segment) {
        double aDistance = Math.hypot(contact.x - segment.a().x, contact.y - segment.a().y);
        double bDistance = Math.hypot(contact.x - segment.b().x, contact.y - segment.b().y);
        return aDistance >= bDistance ? segment.a() : segment.b();
    }

    private Integer roundedX(Point point) {
        return point == null ? null : (int) Math.round(point.x);
    }

    private Integer roundedY(Point point) {
        return point == null ? null : (int) Math.round(point.y);
    }

    private boolean nearBorder(Point p, VisionGeometryClassRegion r, double tolerance) {
        if (p.x < r.x() - tolerance || p.x > r.right() + tolerance
                || p.y < r.y() - tolerance || p.y > r.bottom() + tolerance) {
            return false;
        }
        return distanceToBorder(p, r) <= tolerance;
    }

    private double distanceToBorder(Point p, VisionGeometryClassRegion r) {
        double dx = Math.min(Math.abs(p.x - r.x()), Math.abs(p.x - r.right()));
        double dy = Math.min(Math.abs(p.y - r.y()), Math.abs(p.y - r.bottom()));
        boolean withinX = p.x >= r.x() && p.x <= r.right();
        boolean withinY = p.y >= r.y() && p.y <= r.bottom();
        if (withinX && withinY) {
            return Math.min(dx, dy);
        }
        double cx = clampDouble(p.x, r.x(), r.right());
        double cy = clampDouble(p.y, r.y(), r.bottom());
        return Math.hypot(p.x - cx, p.y - cy);
    }

    private byte[] png(Mat mat) {
        MatOfByte bytes = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", mat, bytes)) {
                return new byte[0];
            }
            return bytes.toArray();
        } finally {
            bytes.release();
        }
    }

    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Map<Integer, List<Integer>> segmentIdsByRoot(Dsu dsu, int segmentCount) {
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        for (int index = 0; index < segmentCount; index++) {
            result.computeIfAbsent(dsu.find(index * 2), ignored -> new ArrayList<>()).add(index);
        }
        return result;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (OpenCvUmlDiagramGeometryAnalyzer.class) {
            if (!loaded) {
                OpenCV.loadLocally();
                loaded = true;
            }
        }
    }

    private record Segment(Point a, Point b, double length) {
    }

    private record RasterDistance(VisionGeometryClassRegion region, double distance, Point point) {
    }

    record RasterEndpointBridge(double distance, double rasterCoverage, boolean accepted, String rejectReason) {
    }

    private record LocalRoi(int x, int y, int width, int height) {
        int right() {
            return x + width - 1;
        }

        int bottom() {
            return y + height - 1;
        }
    }

    private record LocalRasterAttachment(
            LocalRoi roi,
            boolean foregroundSeedFound,
            List<Point> pixels,
            List<RasterDistance> classDistances,
            double contactDistance,
            String result,
            VisionGeometryClassRegion acceptedClass,
            Point contactPoint
    ) {
    }

    record LocalAttachmentDecision(
            String candidateClass,
            Set<String> prospectiveTouched,
            String result,
            boolean accepted
    ) {
        static LocalAttachmentDecision noCandidate(String result, Set<String> directTouched, Set<String> localTouched) {
            Set<String> effective = new LinkedHashSet<>(directTouched);
            effective.addAll(localTouched);
            return new LocalAttachmentDecision(null, effective, result, false);
        }
    }

    private record CandidateDraft(
            VisionGeometryClassRegion a,
            VisionGeometryClassRegion b,
            double score,
            int cropX,
            int cropY,
            int cropWidth,
            int cropHeight,
            Point contactA,
            Point contactB,
            Point innerA,
            Point innerB
    ) {
    }

    private record GeometryGraph(
            Map<Integer, Set<String>> touched,
            Map<Integer, Map<String, Point>> contacts,
            Map<Integer, Double> lengths,
            Map<Integer, List<Integer>> segmentIds
    ) {
    }

    /** Internal evidence trace; persistence is best-effort and cannot affect detection. */
    private static final class GeometryDiagnostics {
        private final String filename;
        private final List<String> segments = new ArrayList<>();
        private final List<String> beforeComponents = new ArrayList<>();
        private final List<String> endpointBridges = new ArrayList<>();
        private final List<String> unionEvents = new ArrayList<>();
        private final List<String> rasterAttachments = new ArrayList<>();
        private final List<String> afterComponents = new ArrayList<>();
        private final List<String> finalPairDecisions = new ArrayList<>();
        private final List<String> edgeCandidates = new ArrayList<>();

        private GeometryDiagnostics(String filename, List<Segment> source) {
            this.filename = filename;
            for (int index = 0; index < source.size(); index++) {
                Segment segment = source.get(index);
                segments.add("{\"id\":" + index + ",\"endpoints\":[" + point(segment.a()) + ","
                        + point(segment.b()) + "],\"length\":" + segment.length() + "}");
            }
        }

        void componentsBefore(
                Dsu dsu,
                List<Segment> segments,
                List<Point> endpoints,
                Map<Integer, Set<String>> directTouches
        ) {
            Map<Integer, List<Integer>> ids = new LinkedHashMap<>();
            for (int index = 0; index < segments.size(); index++) {
                ids.computeIfAbsent(dsu.find(index * 2), ignored -> new ArrayList<>()).add(index);
            }
            ids.keySet().stream().sorted().forEach(root -> {
                List<String> componentEndpoints = new ArrayList<>();
                for (int segmentId : ids.get(root)) {
                    componentEndpoints.add(point(endpoints.get(segmentId * 2)));
                    componentEndpoints.add(point(endpoints.get(segmentId * 2 + 1)));
                }
                List<String> touched = quoted(directTouches.getOrDefault(root, Set.of()));
                beforeComponents.add("{\"root\":" + root + ",\"segmentIds\":" + ids.get(root)
                        + ",\"endpoints\":[" + String.join(",", componentEndpoints)
                        + "],\"directClassContacts\":[" + String.join(",", touched)
                        + "],\"localRasterClassContacts\":[],\"touchedClasses\":[" + String.join(",", touched) + "]}");
            });
        }

        void endpointBridge(
                int rootA,
                int rootB,
                Point endpointA,
                Point endpointB,
                double bridgeDistance,
                RasterEndpointBridge bridge,
                List<String> directA,
                List<String> directB,
                List<RasterDistance> distancesA,
                List<RasterDistance> distancesB
        ) {
            endpointBridges.add("{\"rootA\":" + rootA + ",\"rootB\":" + rootB
                    + ",\"endpointA\":" + point(endpointA) + ",\"endpointB\":" + point(endpointB)
                    + ",\"bridgeDistance\":" + bridgeDistance + ",\"distance\":" + bridge.distance()
                    + ",\"rasterCoverage\":" + bridge.rasterCoverage() + ",\"accepted\":" + bridge.accepted()
                    + ",\"rejectReason\":" + nullable(bridge.rejectReason()) + ",\"endpointADirectClasses\":["
                    + String.join(",", quoted(directA)) + "],\"endpointBDirectClasses\":["
                    + String.join(",", quoted(directB)) + "],\"endpointAClassDistances\":" + distances(distancesA)
                    + ",\"endpointBClassDistances\":" + distances(distancesB) + "}");
        }

        void unionEvent(
                String reason,
                int rootA,
                int rootB,
                int root,
                Set<String> touchesA,
                Set<String> touchesB,
                Set<String> merged
        ) {
            unionEvents.add("{\"reason\":\"" + escape(reason) + "\",\"oldRootA\":" + rootA
                    + ",\"oldRootB\":" + rootB + ",\"newRoot\":" + root + ",\"touchedClassesBeforeA\":["
                    + String.join(",", quoted(touchesA)) + "],\"touchedClassesBeforeB\":["
                    + String.join(",", quoted(touchesB)) + "],\"touchedClassesAfterUnion\":["
                    + String.join(",", quoted(merged)) + "]}");
        }

        void localRasterAttachment(
                int root,
                Point endpoint,
                LocalRasterAttachment attachment,
                Set<String> directBefore,
                Set<String> localBefore,
                LocalAttachmentDecision decision,
                Set<String> effectiveAfter
        ) {
            List<String> candidates = new ArrayList<>();
            for (RasterDistance distance : attachment.classDistances()) {
                candidates.add("{\"classId\":\"" + escape(distance.region().geometryId()) + "\",\"minDistance\":"
                        + distance.distance() + ",\"closestRasterPixel\":" + point(distance.point())
                        + ",\"acceptedByDistance\":" + (distance.distance() <= attachment.contactDistance()) + "}");
            }
            rasterAttachments.add("{\"graphRoot\":" + root + ",\"endpoint\":" + point(endpoint)
                    + ",\"roi\":[" + attachment.roi().x() + "," + attachment.roi().y() + ","
                    + attachment.roi().width() + "," + attachment.roi().height() + "],\"foregroundSeedFound\":"
                    + attachment.foregroundSeedFound() + ",\"localRasterPixelCount\":" + attachment.pixels().size()
                    + ",\"classCandidates\":[" + String.join(",", candidates) + "],\"directTouchedBefore\":["
                    + String.join(",", quoted(directBefore)) + "],\"localTouchedBefore\":["
                    + String.join(",", quoted(localBefore)) + "],\"effectiveTouchedBefore\":["
                    + String.join(",", quoted(effective(directBefore, localBefore))) + "],\"candidateClass\":"
                    + nullable(decision.candidateClass()) + ",\"prospectiveTouched\":["
                    + String.join(",", quoted(decision.prospectiveTouched())) + "],\"result\":\""
                    + decision.result() + "\",\"acceptedClass\":"
                    + nullable(decision.accepted() ? decision.candidateClass() : null) + ",\"contactPoint\":"
                    + (decision.accepted() && attachment.contactPoint() != null ? point(attachment.contactPoint()) : "null")
                    + ",\"effectiveTouchedAfter\":[" + String.join(",", quoted(effectiveAfter)) + "]}");
        }

        void componentsAfter(
                Map<Integer, List<Integer>> segmentIds,
                Map<Integer, Set<String>> directTouched,
                Map<Integer, Set<String>> localTouched,
                Map<Integer, Set<String>> effectiveTouched,
                Map<Integer, Map<String, Point>> contacts
        ) {
            segmentIds.keySet().stream().sorted().forEach(root -> {
                afterComponents.add("{\"root\":" + root + ",\"segmentIds\":" + segmentIds.get(root)
                        + ",\"directTouchedClasses\":[" + String.join(",", quoted(directTouched.getOrDefault(root, Set.of())))
                        + "],\"localTouchedClasses\":[" + String.join(",", quoted(localTouched.getOrDefault(root, Set.of())))
                        + "],\"effectiveTouchedClasses\":[" + String.join(",", quoted(effectiveTouched.getOrDefault(root, Set.of())))
                        + "],\"contacts\":" + contactsJson(contacts.getOrDefault(root, Map.of())) + "}");
            });
        }

        void finalPairDecisions(Map<Integer, Set<String>> touched) {
            touched.keySet().stream().sorted().forEach(root -> {
                List<String> classes = new ArrayList<>(touched.get(root));
                if (classes.size() == 2) {
                    finalPairDecisions.add("{\"root\":" + root + ",\"touchedClasses\":["
                            + String.join(",", quoted(classes)) + "],\"emittedPair\":\""
                            + escape(pair(classes.get(0), classes.get(1))) + "\",\"rejectReason\":null}");
                } else {
                    String reason = classes.isEmpty() ? "NO_CLASS" : classes.size() == 1
                            ? "ONE_CLASS_ONLY" : "MORE_THAN_TWO_CLASSES";
                    finalPairDecisions.add("{\"root\":" + root + ",\"touchedClasses\":["
                            + String.join(",", quoted(classes)) + "],\"emittedPair\":null,\"rejectReason\":\""
                            + reason + "\"}");
                }
            });
        }

        void edgeCandidate(String pair, int root, double score) {
            edgeCandidates.add("{\"pair\":\"" + escape(pair) + "\",\"sourceComponent\":" + root
                    + ",\"score\":" + score + "}");
        }

        void write(List<VisionGeometryEdgeCandidate> ignored) {
            Path report = Path.of("build", "reports", "opencv-diagram-geometry",
                    filename.replaceAll("[^A-Za-z0-9._-]", "_"), "graph-debug.json");
            String json = "{\n  \"segments\":" + jsonArray(segments)
                    + ",\n  \"componentsBeforeBridge\":" + jsonArray(beforeComponents)
                    + ",\n  \"endpointBridgeAttempts\":" + jsonArray(endpointBridges)
                    + ",\n  \"unionEvents\":" + jsonArray(unionEvents)
                    + ",\n  \"localRasterAttachmentAttempts\":" + jsonArray(rasterAttachments)
                    + ",\n  \"componentsAfterBridge\":" + jsonArray(afterComponents)
                    + ",\n  \"finalPairDecisions\":" + jsonArray(finalPairDecisions)
                    + ",\n  \"edgeCandidates\":" + jsonArray(edgeCandidates) + "\n}";
            try {
                Files.createDirectories(report.getParent());
                Files.writeString(report, json);
            } catch (IOException ignoredException) {
                // Internal diagnostics must not change analyzer availability.
            }
        }

        private static List<String> componentJson(
                Map<Integer, List<Integer>> ids,
                Map<Integer, Set<String>> touched,
                Map<Integer, Map<String, Point>> contacts
        ) {
            List<String> result = new ArrayList<>();
            ids.keySet().stream().sorted().forEach(root -> {
                List<String> contactValues = new ArrayList<>();
                for (Map.Entry<String, Point> contact : contacts.getOrDefault(root, Map.of()).entrySet()) {
                    contactValues.add("\"" + escape(contact.getKey()) + "\":" + point(contact.getValue()));
                }
                List<String> classes = touched.getOrDefault(root, Set.of()).stream()
                        .map(value -> "\"" + escape(value) + "\"").toList();
                result.add("{\"root\":" + root + ",\"segmentIds\":" + ids.get(root)
                        + ",\"endpointClassTouches\":[" + String.join(",", classes) + "],\"contacts\":{"
                        + String.join(",", contactValues) + "}}");
            });
            return result;
        }

        private static List<String> quoted(Iterable<String> values) {
            List<String> result = new ArrayList<>();
            for (String value : values) {
                result.add("\"" + escape(value) + "\"");
            }
            return result;
        }

        private static Set<String> effective(Set<String> directTouched, Set<String> localTouched) {
            Set<String> result = new LinkedHashSet<>(directTouched);
            result.addAll(localTouched);
            return result;
        }

        private static String contactsJson(Map<String, Point> contacts) {
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, Point> contact : contacts.entrySet()) {
                result.add("\"" + escape(contact.getKey()) + "\":" + point(contact.getValue()));
            }
            return "{" + String.join(",", result) + "}";
        }

        private static String jsonArray(List<String> values) {
            return values.isEmpty() ? " []" : " [\n    " + String.join(",\n    ", values) + "\n  ]";
        }

        private static String distances(List<RasterDistance> values) {
            List<String> result = new ArrayList<>();
            for (RasterDistance distance : values) {
                result.add("{\"classId\":\"" + escape(distance.region().geometryId()) + "\",\"minDistance\":"
                        + distance.distance() + "}");
            }
            return "[" + String.join(",", result) + "]";
        }

        private static String pair(String a, String b) {
            return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
        }

        private static String point(Point point) {
            return "[" + point.x + "," + point.y + "]";
        }

        private static String nullable(String value) {
            return value == null ? "null" : "\"" + escape(value) + "\"";
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static final class Dsu {
        private final int[] parent;
        private final byte[] rank;

        Dsu(int size) {
            this.parent = new int[size];
            this.rank = new byte[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        void union(int a, int b) {
            unionAndReturnRoot(a, b);
        }

        int unionAndReturnRoot(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return ra;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
                return rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
                return ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
                return ra;
            }
        }
    }
}
