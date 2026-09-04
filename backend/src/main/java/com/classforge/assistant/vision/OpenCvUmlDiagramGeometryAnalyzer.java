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
            GeometryGraph graph = graph(segments, regions, minDimension);
            List<VisionGeometryEdgeCandidate> candidates = candidates(
                    regions,
                    graph,
                    image.cols(),
                    image.rows()
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
            int minDimension
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
        double joinSquared = joinDistance * joinDistance;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            for (int j = i + 1; j < points.size(); j++) {
                Point b = points.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                if (dx * dx + dy * dy <= joinSquared) {
                    dsu.union(i, j);
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
                    dsu.union(i * 2, j * 2);
                }
            }
        }

        double contactDistance = Math.max(10.0, minDimension / 75.0);
        Map<Integer, Set<String>> touched = new HashMap<>();
        Map<Integer, Map<String, Point>> contacts = new HashMap<>();
        Map<Integer, Double> lengths = new HashMap<>();

        for (int i = 0; i < segments.size(); i++) {
            int root = dsu.find(i * 2);
            lengths.merge(root, segments.get(i).length(), Double::sum);
        }
        for (int i = 0; i < points.size(); i++) {
            int root = dsu.find(i);
            Point point = points.get(i);
            for (VisionGeometryClassRegion region : regions) {
                if (nearBorder(point, region, contactDistance)) {
                    touched.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(region.geometryId());
                    Map<String, Point> componentContacts = contacts.computeIfAbsent(
                            root,
                            ignored -> new LinkedHashMap<>()
                    );
                    Point previous = componentContacts.get(region.geometryId());
                    if (previous == null
                            || distanceToBorder(point, region) < distanceToBorder(previous, region)) {
                        componentContacts.put(region.geometryId(), point);
                    }
                }
            }
        }
        return new GeometryGraph(touched, contacts, lengths);
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
            int imageWidth,
            int imageHeight
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
                    contactA, contactB
            ));
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
                    (int) Math.round(draft.contactB().x), (int) Math.round(draft.contactB().y)
            ));
        }
        return List.copyOf(result);
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

    private record CandidateDraft(
            VisionGeometryClassRegion a,
            VisionGeometryClassRegion b,
            double score,
            int cropX,
            int cropY,
            int cropWidth,
            int cropHeight,
            Point contactA,
            Point contactB
    ) {
    }

    private record GeometryGraph(
            Map<Integer, Set<String>> touched,
            Map<Integer, Map<String, Point>> contacts,
            Map<Integer, Double> lengths
    ) {
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
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
        }
    }
}
