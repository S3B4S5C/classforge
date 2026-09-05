package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects physical UML class rectangles before any VLM pixel localization.
 *
 * The detector deliberately does not assign semantic class refs. It only finds
 * rectangle-like regions and labels them B1, B2, ... for a subsequent closed
 * VLM mapping pass. This mirrors the CV-first separation used by diagram
 * extraction pipelines such as FlowExtract/ReSECDI: geometry first, semantics
 * second.
 */
@Component
public class OpenCvUmlClassRegionDetector implements UmlClassRegionDetector {

    private static volatile boolean loaded;

    @Override
    public UmlClassRegionDetection detect(VisionNormalizedImage input, int expectedClassCount) {
        if (expectedClassCount < 1) {
            throw new AssistantPlanningException("Se requiere al menos una clase semantica para detectar regiones.");
        }
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
            throw new AssistantPlanningException("OpenCV no pudo decodificar la imagen para detectar cajas UML.");
        }

        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();
        Mat contoursInput = new Mat();
        Mat overlay = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        try {
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

            // A very small close repairs marker gaps in hand-drawn rectangle borders
            // without connecting distant relationship lines.
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            try {
                Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel);
            } finally {
                kernel.release();
            }

            binary.copyTo(contoursInput);
            Mat hierarchy = new Mat();
            try {
                Imgproc.findContours(
                        contoursInput,
                        contours,
                        hierarchy,
                        Imgproc.RETR_TREE,
                        Imgproc.CHAIN_APPROX_SIMPLE
                );
            } finally {
                hierarchy.release();
            }

            int imageArea = Math.max(1, image.cols() * image.rows());
            int minDimension = Math.max(64, Math.min(image.cols(), image.rows()));
            int minWidth = Math.max(45, minDimension / 18);
            int minHeight = Math.max(55, minDimension / 14);
            double minArea = imageArea * 0.008;
            double maxArea = imageArea * 0.080;

            List<Candidate> strictCandidates = new ArrayList<>();
            List<Candidate> relaxedCandidates = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect box = Imgproc.boundingRect(contour);
                double boxArea = (double) box.width * box.height;
                double aspect = box.width / (double) Math.max(1, box.height);
                double contourArea = Math.abs(Imgproc.contourArea(contour));
                double rectangularity = contourArea / Math.max(1.0, boxArea);
                MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                try {
                    double perimeter = Imgproc.arcLength(curve, true);
                    Imgproc.approxPolyDP(curve, approx, Math.max(2.0, perimeter * 0.02), true);
                    int vertices = approx.toArray().length;
                    Candidate candidate = new Candidate(box, rectangularity, boxArea);
                    if (box.width >= minWidth && box.height >= minHeight
                            && boxArea >= minArea && boxArea <= maxArea
                            && aspect >= 0.24 && aspect <= 4.2
                            && rectangularity >= 0.62
                            && vertices >= 4 && vertices <= 12) {
                        strictCandidates.add(candidate);
                    }
                    int longSide = Math.max(box.width, box.height);
                    int shortSide = Math.min(box.width, box.height);
                    if (longSide >= Math.max(80, minDimension / 8)
                            && shortSide >= Math.max(24, minDimension / 40)
                            && boxArea >= imageArea * 0.005 && boxArea <= maxArea
                            && aspect >= 0.16 && aspect <= 6.0
                            && rectangularity >= 0.50
                            && vertices >= 4 && vertices <= 16) {
                        relaxedCandidates.add(candidate);
                    }
                } finally {
                    curve.release();
                    approx.release();
                }
            }

            // Strict contours are geometric primitives, not final class decisions.
            // RETR_TREE commonly returns near-identical inner/outer contour pairs.
            strictCandidates.sort(Comparator
                    .comparingDouble(Candidate::area).reversed()
                    .thenComparing(Comparator.comparingDouble(Candidate::rectangularity).reversed()));
            List<Candidate> strictPrimitives = new ArrayList<>();
            for (Candidate candidate : strictCandidates) {
                if (strictPrimitives.stream().noneMatch(existing -> nearIdentical(candidate.box(), existing.box()))) {
                    strictPrimitives.add(candidate);
                }
            }
            if (strictPrimitives.isEmpty()) {
                throw new AssistantPlanningException(
                        "OpenCV no detecto primitivas rectangulares UML estrictas."
                );
            }

            List<Candidate> primitives = new ArrayList<>(relaxedCandidates);
            for (Candidate strict : strictPrimitives) {
                if (primitives.stream().noneMatch(candidate -> nearIdentical(candidate.box(), strict.box()))) {
                    primitives.add(strict);
                }
            }
            List<FinalCandidate> hypotheses = new ArrayList<>();
            DetectionDiagnostics diagnostics = new DetectionDiagnostics(input.originalFilename());
            for (Candidate seed : strictPrimitives) {
                hypotheses.add(FinalCandidate.direct(seed,
                        hasCompleteFrameEvidence(binary, seed.box(), minDimension, diagnostics)));
                hypotheses.addAll(compartmentChains(binary, seed, primitives, maxArea, minDimension, diagnostics));
                hypotheses.addAll(houghHypotheses(binary, seed, minDimension, diagnostics));
            }
            List<FinalCandidate> finalCandidates;
            try {
                finalCandidates = selectFinalHypotheses(hypotheses, expectedClassCount, diagnostics);
            } catch (AssistantPlanningException exception) {
                diagnostics.write(List.of());
                throw exception;
            }
            validateFinalCandidates(finalCandidates, expectedClassCount, image.cols(), image.rows());

            // Stable spatial ordering makes B ids deterministic between runs. The VLM
            // still maps Bx -> classRef, so no semantic assumption is made here.
            finalCandidates.sort(Comparator
                    .comparingInt((FinalCandidate c) -> c.box().y)
                    .thenComparingInt(c -> c.box().x));

            List<VisionGeometryClassRegion> regions = new ArrayList<>();
            int index = 1;
            for (FinalCandidate candidate : finalCandidates) {
                Rect box = candidate.box();
                double confidence = candidate.confidence();
                regions.add(new VisionGeometryClassRegion(
                        "B" + index++,
                        null,
                        box.x,
                        box.y,
                        box.width,
                        box.height,
                        confidence
                ));
            }
            diagnostics.write(finalCandidates);

            overlay = image.clone();
            for (VisionGeometryClassRegion region : regions) {
                Imgproc.rectangle(
                        overlay,
                        new Point(region.x(), region.y()),
                        new Point(region.right(), region.bottom()),
                        new Scalar(0, 0, 255),
                        3
                );
                Imgproc.putText(
                        overlay,
                        region.geometryId(),
                        new Point(region.x() + 6, Math.max(24, region.y() + 26)),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.8,
                        new Scalar(255, 0, 0),
                        2
                );
            }

            return new UmlClassRegionDetection(
                    List.copyOf(regions),
                    png(binary),
                    png(overlay)
            );
        } finally {
            for (MatOfPoint contour : contours) {
                contour.release();
            }
            gray.release();
            blurred.release();
            binary.release();
            contoursInput.release();
            overlay.release();
            image.release();
        }
    }

    private List<FinalCandidate> compartmentChains(
            Mat binary,
            Candidate seed,
            List<Candidate> primitives,
            double maxArea,
            int minDimension,
            DetectionDiagnostics diagnostics
    ) {
        List<FinalCandidate> results = new ArrayList<>();
        List<ChainState> pending = List.of(new ChainState(seed.box(), List.of(seed), 0.0));
        Axis chainAxis = Axis.of(seed.box());
        // Exhaust every accepted branch before calling it maximal. A depth cap
        // could make a still-growable partial reconstruction final-eligible.
        while (!pending.isEmpty()) {
            List<ChainState> next = new ArrayList<>();
            for (ChainState state : pending) {
                boolean merged = false;
                for (Candidate companion : primitives) {
                    if (state.consumed().contains(companion)) {
                        continue;
                    }
                    if (enclosesCompartment(state.box(), companion.box())) {
                        List<Candidate> consumed = new ArrayList<>(state.consumed());
                        consumed.add(companion);
                        ChainState expanded = new ChainState(companion.box(), List.copyOf(consumed), state.score() + companion.rectangularity());
                        next.add(expanded);
                        results.add(FinalCandidate.companion(seed, expanded, false));
                        merged = true;
                        diagnostics.companion(seed.box(), state.box(), companion.box(), "enclosing", expanded.score());
                        continue;
                    }
                    CompanionMatch match = companionMatch(state.box(), companion, chainAxis, maxArea, minDimension);
                    diagnostics.companion(seed.box(), state.box(), companion.box(),
                            match == null ? "rejected" : "accepted", match == null ? null : match.score());
                    if (match == null) {
                        continue;
                    }
                    List<Candidate> consumed = new ArrayList<>(state.consumed());
                    consumed.add(companion);
                    ChainState expanded = new ChainState(match.union(), List.copyOf(consumed), state.score() + match.score());
                    next.add(expanded);
                    // The shared boundary is physical evidence of the compartment divider.
                    results.add(FinalCandidate.companion(seed, expanded, false));
                    merged = true;
                }
                if (!merged && state.consumed().size() > 1) {
                    results.add(FinalCandidate.companion(seed, state, true));
                    diagnostics.maximalChain(seed.box(), state.box(), state.consumed().size());
                }
            }
            pending = next;
        }
        return results;
    }

    private boolean enclosesCompartment(Rect inner, Rect outer) {
        Axis axis = Axis.of(inner);
        if (Axis.of(outer) != axis || !contains(outer, inner) || nearIdentical(inner, outer)) {
            return false;
        }
        return longAxisGrowth(inner, outer, axis) <= axis.longSide(inner) * 0.10
                && axis.shortSide(outer) - axis.shortSide(inner) <= axis.shortSide(inner) * 0.75;
    }

    private List<FinalCandidate> houghHypotheses(
            Mat binary,
            Candidate seed,
            int minDimension,
            DetectionDiagnostics diagnostics
    ) {
        Axis axis = Axis.of(seed.box());
        int maxExpansion = clamp((int) Math.round(axis.shortSide(seed.box()) * 0.75), 36,
                (int) Math.round(minDimension * 0.14));
        List<HoughMatch> matches = new ArrayList<>();
        for (int side : List.of(-1, 1)) {
            Rect roi = expansionRoi(seed.box(), axis, side, maxExpansion, binary.cols(), binary.rows());
            Mat local = new Mat(binary, roi);
            Mat lines = new Mat();
            try {
                Imgproc.HoughLinesP(local, lines, 1, Math.PI / 180.0,
                        Math.max(20, (int) Math.round(axis.longSide(seed.box()) * 0.16)),
                        (int) Math.round(axis.longSide(seed.box()) * 0.45),
                        Math.max(10, minDimension / 90));
                List<Line> evidence = lines(lines, roi.x, roi.y);
                for (Line line : evidence) {
                    diagnostics.hough("closing-border", seed.box(), roi, side, line);
                    if (!parallel(line, axis, 15) || longOverlap(line, seed.box(), axis) < 0.55) {
                        continue;
                    }
                    double offset = outwardOffset(line, seed.box(), axis, side);
                    double shortSide = axis.shortSide(seed.box());
                    if (offset < shortSide * 0.12 || offset > shortSide * 0.75) {
                        continue;
                    }
                    Rect expanded = expandTo(seed.box(), line, axis, side, binary.cols(), binary.rows());
                    double closingCoord = closingCoord(line, axis, expanded);
                    CornerContinuations continuations = cornerContinuations(
                            binary, evidence, seed.box(), line, axis, side, closingCoord, minDimension, diagnostics
                    );
                    diagnostics.houghHypothesis(seed.box(), expanded, side, line, closingCoord,
                            continuations.hough(), continuations.binaryBridge());
                    if (continuations.total() == 0) {
                        continue;
                    }
                    double plausibility = 1.0 - clamp(Math.abs(offset / shortSide - 0.435) / 0.315, 0, 1);
                    double continuationEvidence = continuations.hough() > 0 || continuations.binaryBridge() > 0
                            ? 1.0 : 0.0;
                    double score = 0.55 * longOverlap(line, seed.box(), axis)
                            + 0.25 * continuationEvidence + 0.20 * plausibility;
                    if (score >= 0.70) {
                        matches.add(new HoughMatch(line, side, score, closingCoord, continuations.total()));
                    }
                }
            } finally {
                lines.release();
                local.release();
            }
        }
        matches.sort(Comparator.comparingDouble(HoughMatch::score).reversed()
                .thenComparingInt(HoughMatch::side)
                .thenComparing(match -> axis.perpendicularCenter(match.line())));
        List<FinalCandidate> results = new ArrayList<>();
        for (HoughMatch match : matches) {
            Rect expanded = expandTo(seed.box(), match.line(), axis, match.side(), binary.cols(), binary.rows());
            FinalCandidate candidate = FinalCandidate.hough(seed, expanded, match.score());
            // Closing border plus corner continuation already proves the old seed edge is internal.
            results.add(candidate);
        }
        return results;
    }

    private CompanionMatch companionMatch(Rect seed, Candidate companion, Axis axis, double maxArea, int minDimension) {
        if (Axis.of(companion.box()) != axis || nearIdentical(seed, companion.box())
                || contains(seed, companion.box()) || contains(companion.box(), seed)) {
            return null;
        }
        double overlap = longAxisOverlap(seed, companion.box(), axis);
        if (overlap < 0.72) {
            return null;
        }
        int side = adjacentSide(seed, companion.box(), axis);
        if (side == 0) {
            return null;
        }
        int gap = shortAxisGap(seed, companion.box(), axis, side);
        int tolerance = Math.max(12, minDimension / 80);
        double depth = addedDepth(seed, companion.box(), axis, side);
        Rect union = union(seed, companion.box());
        if (gap > tolerance || depth < axis.shortSide(seed) * 0.12 || depth > axis.shortSide(seed) + tolerance
                || longAxisGrowth(seed, union, axis) > axis.longSide(seed) * 0.10
                || (double) union.width * union.height > maxArea * 1.10) {
            return null;
        }
        double centerAlignment = 1.0 - clamp(
                longAxisCenterDelta(seed, companion.box(), axis) / (0.25 * axis.longSide(seed)), 0, 1
        );
        double adjacency = 1.0 - clamp(gap / (double) tolerance, 0, 1);
        double score = 0.40 * overlap + 0.25 * centerAlignment + 0.20 * companion.rectangularity() + 0.15 * adjacency;
        return score >= 0.72 ? new CompanionMatch(companion, union, side, score) : null;
    }

    private List<FinalCandidate> selectFinalHypotheses(
            List<FinalCandidate> hypotheses,
            int expected,
            DetectionDiagnostics diagnostics
    ) {
        List<FinalCandidate> ranked = hypotheses.stream()
                .filter(candidate -> candidate.box().width > 0 && candidate.box().height > 0)
                .sorted(Comparator.comparingDouble(FinalCandidate::selectionScore).reversed()
                        .thenComparing(candidate -> candidate.box().x)
                        .thenComparing(candidate -> candidate.box().y))
                .toList();
        diagnostics.hypotheses(ranked);
        List<FinalCandidate> ordered = ranked.stream()
                .filter(candidate -> candidate.box().width > 0 && candidate.box().height > 0
                        && candidate.evidenceComplete())
                .toList();
        diagnostics.topCandidates(ordered);
        List<FinalCandidate> selected = new ArrayList<>();
        for (FinalCandidate candidate : ordered) {
            if (selected.stream().noneMatch(existing -> collides(candidate.box(), existing.box()))) {
                selected.add(candidate);
                if (selected.size() == expected) {
                    return selected;
                }
            }
        }
        throw new AssistantPlanningException("OpenCV genero " + selected.size() + " hipotesis UML no ambiguas; se esperaban "
                + expected + ". Se rechaza el modo hibrido sin sintetizar regiones.");
    }

    boolean hasInternalDivider(Mat binary, Rect box, int minDimension) {
        return hasInternalDivider(binary, box, minDimension, null);
    }

    private boolean hasInternalDivider(Mat binary, Rect box, int minDimension, DetectionDiagnostics diagnostics) {
        return hasInternalDivider(binary, box, Axis.of(box), minDimension, diagnostics, "internal-divider");
    }

    private boolean hasCompleteFrameEvidence(Mat binary, Rect box, int minDimension, DetectionDiagnostics diagnostics) {
        Axis axis = Axis.of(box);
        return hasInternalDivider(binary, box, axis, minDimension, diagnostics, "internal-divider")
                || hasInternalDivider(binary, box, axis.other(), minDimension, diagnostics, "compartment-divider");
    }

    private boolean hasInternalDivider(
            Mat binary,
            Rect box,
            Axis axis,
            int minDimension,
            DetectionDiagnostics diagnostics,
            String context
    ) {
        double shortSpan = axis.shortSide(box);
        double shortAxisInset = Math.max(8.0, Math.round(shortSpan * 0.12));
        for (Line line : houghLines(binary, box, axis, minDimension, diagnostics, context)) {
            if (!parallel(line, axis, 15) || longOverlap(line, box, axis) < 0.55) {
                continue;
            }
            double lineShortMin = Math.min(axis.shortCoordinate(line), axis.shortCoordinateEnd(line));
            double lineShortMax = Math.max(axis.shortCoordinate(line), axis.shortCoordinateEnd(line));
            if (lineShortMin <= axis.shortStart(box) + shortAxisInset
                    || lineShortMax >= axis.shortEnd(box) - shortAxisInset) {
                continue;
            }
            if (hasTwoSidedFrameSupport(binary, line, axis, shortSpan)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTwoSidedFrameSupport(Mat binary, Line divider, Axis axis, double shortSpan) {
        int probeDepth = Math.max(6, (int) Math.round(shortSpan * 0.06));
        for (double endpoint : List.of(
                axis.longCoordinate(divider.x1(), divider.y1()),
                axis.longCoordinate(divider.x2(), divider.y2())
        )) {
            double transverse = axis.perpendicularCenter(divider);
            if (hasBinaryEvidence(binary, axis, endpoint, transverse - probeDepth)
                    && hasBinaryEvidence(binary, axis, endpoint, transverse + probeDepth)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBinaryEvidence(Mat binary, Axis axis, double longCoordinate, double shortCoordinate) {
        int probeTolerance = 4;
        for (int longOffset = -probeTolerance; longOffset <= probeTolerance; longOffset++) {
            for (int shortOffset = -probeTolerance; shortOffset <= probeTolerance; shortOffset++) {
                int x = (int) Math.round(axis == Axis.VERTICAL
                        ? shortCoordinate + shortOffset : longCoordinate + longOffset);
                int y = (int) Math.round(axis == Axis.VERTICAL
                        ? longCoordinate + longOffset : shortCoordinate + shortOffset);
                if (x >= 0 && x < binary.cols() && y >= 0 && y < binary.rows()) {
                    double[] pixel = binary.get(y, x);
                    if (pixel != null && pixel.length > 0 && pixel[0] > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasSeedBoundaryDivider(Mat binary, Rect outer, Rect seed, int minDimension) {
        Axis axis = Axis.of(seed);
        if (axis.shortStart(seed) <= axis.shortStart(outer)
                && axis.shortEnd(seed) >= axis.shortEnd(outer)) {
            return false;
        }
        return hasInternalDivider(binary, outer, minDimension);
    }

    private List<Line> houghLines(
            Mat binary,
            Rect requested,
            Axis axis,
            int minDimension,
            DetectionDiagnostics diagnostics,
            String context
    ) {
        Rect roi = clamp(requested, binary.cols(), binary.rows());
        Mat local = new Mat(binary, roi);
        Mat detected = new Mat();
        try {
            Imgproc.HoughLinesP(local, detected, 1, Math.PI / 180.0,
                    Math.max(20, (int) Math.round(axis.longSide(requested) * 0.16)),
                    (int) Math.round(axis.longSide(requested) * 0.45),
                    Math.max(10, minDimension / 90));
            List<Line> result = lines(detected, roi.x, roi.y);
            if (diagnostics != null) {
                for (Line line : result) {
                    diagnostics.hough(context, requested, roi, 0, line);
                }
            }
            return result;
        } finally {
            detected.release();
            local.release();
        }
    }

    private List<Line> lines(Mat lines, int xOffset, int yOffset) {
        List<Line> result = new ArrayList<>();
        for (int row = 0; row < lines.rows(); row++) {
            double[] values = lines.get(row, 0);
            if (values != null && values.length >= 4) {
                result.add(new Line(values[0] + xOffset, values[1] + yOffset, values[2] + xOffset, values[3] + yOffset));
            }
        }
        return result;
    }

    private CornerContinuations cornerContinuations(
            Mat binary,
            List<Line> lines,
            Rect seed,
            Line closing,
            Axis axis,
            int side,
            double closingCoord,
            int minDimension,
            DetectionDiagnostics diagnostics
    ) {
        int houghSupported = 0;
        int binaryBridgeSupported = 0;
        double tolerance = Math.max(18, minDimension / 60);
        for (double corner : axis.longCorners(seed)) {
            boolean found = false;
            for (Line candidate : lines) {
                if (!parallel(candidate, axis.other(), 20)) {
                    continue;
                }
                // Compare both lines at the projected closing border, rather than
                // at a raw Hough endpoint or the seed edge.
                double cornerProjection = axis.longAtShortCoordinate(candidate, closingCoord);
                if (Double.isNaN(cornerProjection)
                        || Math.abs(cornerProjection - corner) > tolerance
                        || reachesClosingBorder(candidate, axis, side, seed, closingCoord, corner) < 0.55) {
                    continue;
                }
                found = true;
                break;
            }
            if (found) {
                houghSupported++;
                diagnostics.projectedCorner(seed, closing, side, corner, "hough");
            } else if (hasBinaryProjectedCornerBridge(binary, seed, axis, side, closingCoord, corner)) {
                binaryBridgeSupported++;
                diagnostics.projectedCorner(seed, closing, side, corner, "binary-bridge");
            } else {
                diagnostics.projectedCorner(seed, closing, side, corner, "rejected");
            }
        }
        return new CornerContinuations(houghSupported, binaryBridgeSupported);
    }

    /**
     * Uses threshold pixels only after Hough continuation evidence is absent.
     * The projected corner must bridge the entire seed-to-closing path.
     */
    boolean hasBinaryProjectedCornerBridge(
            Mat binary,
            Rect seed,
            int side,
            double closingCoord,
            double corner
    ) {
        return hasBinaryProjectedCornerBridge(binary, seed, Axis.of(seed), side, closingCoord, corner);
    }

    private boolean hasBinaryProjectedCornerBridge(
            Mat binary,
            Rect seed,
            Axis axis,
            int side,
            double closingCoord,
            double corner
    ) {
        int start = (int) Math.round(side < 0 ? axis.shortStart(seed) : axis.shortEnd(seed));
        int end = (int) Math.round(closingCoord);
        int step = end < start ? -1 : 1;
        for (int coordinate = start; coordinate != end + step; coordinate += step) {
            if (!hasBinaryEvidence(binary, axis, corner, coordinate)) {
                return false;
            }
        }
        return true;
    }

    private double reachesClosingBorder(
            Line continuation,
            Axis axis,
            int side,
            Rect seed,
            double closingCoord,
            double corner
    ) {
        double start = axis.shortStart(seed);
        double target = closingCoord;
        double projected = axis.shortAtLongCoordinate(continuation, corner);
        double reach = side < 0 ? start - projected : projected - axis.shortEnd(seed);
        return reach / Math.max(1.0, Math.abs(target - (side < 0 ? axis.shortStart(seed) : axis.shortEnd(seed))));
    }

    private boolean parallel(Line line, Axis axis, double degrees) {
        double angle = Math.toDegrees(Math.atan2(line.y2() - line.y1(), line.x2() - line.x1()));
        double target = axis == Axis.VERTICAL ? 90.0 : 0.0;
        double delta = Math.abs(angle - target) % 180.0;
        delta = Math.min(delta, 180.0 - delta);
        return delta <= degrees;
    }

    private double longOverlap(Line line, Rect box, Axis axis) {
        double low = Math.max(Math.min(axis.longCoordinate(line.x1(), line.y1()), axis.longCoordinate(line.x2(), line.y2())), axis.longStart(box));
        double high = Math.min(Math.max(axis.longCoordinate(line.x1(), line.y1()), axis.longCoordinate(line.x2(), line.y2())), axis.longEnd(box));
        return Math.max(0, high - low) / Math.max(1.0, axis.longSide(box));
    }

    private double outwardOffset(Line line, Rect seed, Axis axis, int side) {
        double position = axis.perpendicularCenter(line);
        return side < 0 ? axis.shortStart(seed) - position : position - axis.shortEnd(seed);
    }

    private Rect expansionRoi(Rect seed, Axis axis, int side, int expansion, int width, int height) {
        int pad = 8;
        if (axis == Axis.VERTICAL) {
            int x = side < 0 ? seed.x - expansion - pad : seed.x + seed.width - pad;
            return clamp(new Rect(x, seed.y - pad, expansion + pad * 2, seed.height + pad * 2), width, height);
        }
        int y = side < 0 ? seed.y - expansion - pad : seed.y + seed.height - pad;
        return clamp(new Rect(seed.x - pad, y, seed.width + pad * 2, expansion + pad * 2), width, height);
    }

    private Rect expandTo(Rect seed, Line line, Axis axis, int side, int width, int height) {
        int border = (int) Math.round(closingCoord(line, axis, seed));
        Rect result = axis == Axis.VERTICAL
                ? (side < 0 ? new Rect(border, seed.y, seed.x + seed.width - border, seed.height)
                : new Rect(seed.x, seed.y, border - seed.x, seed.height))
                : (side < 0 ? new Rect(seed.x, border, seed.width, seed.y + seed.height - border)
                : new Rect(seed.x, seed.y, seed.width, border - seed.y));
        return clamp(result, width, height);
    }

    private double closingCoord(Line closing, Axis axis, Rect union) {
        return axis.shortAtLongCoordinate(closing, (axis.longStart(union) + axis.longEnd(union)) / 2.0);
    }

    private void validateFinalCandidates(List<FinalCandidate> candidates, int expected, int width, int height) {
        List<String> violations = new ArrayList<>();
        if (candidates.size() != expected) {
            violations.add("produjo " + candidates.size() + " marcos, se esperaban " + expected);
        }
        for (FinalCandidate candidate : candidates) {
            if (!contains(candidate.box(), candidate.seed().box()) || !inside(candidate.box(), width, height)) {
                violations.add("marco invalido para semilla " + describe(candidate.seed().box()));
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                if (intersectionOverUnion(candidates.get(i).box(), candidates.get(j).box()) >= 0.18
                        || centerInside(candidates.get(i).box(), candidates.get(j).box())
                        || centerInside(candidates.get(j).box(), candidates.get(i).box())) {
                    violations.add("marcos ambiguamente solapados para semillas "
                            + describe(candidates.get(i).seed().box()) + " y "
                            + describe(candidates.get(j).seed().box()));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssistantPlanningException(
                    "OpenCV rechazo los marcos UML externos: " + String.join("; ", violations) + "."
            );
        }
    }

    private boolean collidesWithCandidates(
            FinalCandidate candidate,
            List<FinalCandidate> accepted,
            List<Candidate> selected
    ) {
        for (FinalCandidate existing : accepted) {
            if (collides(candidate.box(), existing.box())) {
                return true;
            }
        }
        for (Candidate otherSeed : selected) {
            if (otherSeed != candidate.seed() && collides(candidate.box(), otherSeed.box())) {
                return true;
            }
        }
        return false;
    }

    private boolean collides(Rect a, Rect b) {
        return intersectionOverUnion(a, b) >= 0.18 || centerInside(a, b) || centerInside(b, a);
    }

    private boolean nearIdentical(Rect a, Rect b) {
        return intersectionOverUnion(a, b) >= 0.85;
    }

    private double longAxisOverlap(Rect a, Rect b, Axis axis) {
        int overlap = Math.max(0, Math.min(axis.longEnd(a), axis.longEnd(b)) - Math.max(axis.longStart(a), axis.longStart(b)));
        return overlap / (double) Math.max(1, Math.min(axis.longSide(a), axis.longSide(b)));
    }

    private int adjacentSide(Rect seed, Rect companion, Axis axis) {
        boolean before = axis.shortStart(companion) < axis.shortStart(seed)
                && axis.shortEnd(companion) < axis.shortEnd(seed);
        boolean after = axis.shortEnd(companion) > axis.shortEnd(seed)
                && axis.shortStart(companion) > axis.shortStart(seed);
        return before == after ? 0 : before ? -1 : 1;
    }

    private int shortAxisGap(Rect seed, Rect companion, Axis axis, int side) {
        return Math.max(0, side < 0 ? axis.shortStart(seed) - axis.shortEnd(companion)
                : axis.shortStart(companion) - axis.shortEnd(seed));
    }

    private double addedDepth(Rect seed, Rect companion, Axis axis, int side) {
        return side < 0 ? axis.shortStart(seed) - axis.shortStart(companion)
                : axis.shortEnd(companion) - axis.shortEnd(seed);
    }

    private double longAxisGrowth(Rect seed, Rect union, Axis axis) {
        return Math.max(axis.longStart(seed) - axis.longStart(union), axis.longEnd(union) - axis.longEnd(seed));
    }

    private double longAxisCenterDelta(Rect a, Rect b, Axis axis) {
        return Math.abs((axis.longStart(a) + axis.longEnd(a)) / 2.0 - (axis.longStart(b) + axis.longEnd(b)) / 2.0);
    }

    private Rect union(Rect a, Rect b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        return new Rect(x, y, Math.max(a.x + a.width, b.x + b.width) - x, Math.max(a.y + a.height, b.y + b.height) - y);
    }

    private String describe(Rect box) {
        return box.x + "," + box.y + " " + box.width + "x" + box.height;
    }

    private Rect clamp(Rect box, int width, int height) {
        int x = Math.max(0, Math.min(box.x, width - 1));
        int y = Math.max(0, Math.min(box.y, height - 1));
        int right = Math.max(x + 1, Math.min(box.x + box.width, width));
        int bottom = Math.max(y + 1, Math.min(box.y + box.height, height));
        return new Rect(x, y, right - x, bottom - y);
    }

    private boolean contains(Rect outer, Rect inner) {
        return inner.x >= outer.x && inner.y >= outer.y
                && inner.x + inner.width <= outer.x + outer.width && inner.y + inner.height <= outer.y + outer.height;
    }

    private boolean inside(Rect box, int width, int height) {
        return box.x >= 0 && box.y >= 0 && box.x + box.width <= width && box.y + box.height <= height;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean centerInside(Rect inner, Rect outer) {
        double cx = inner.x + inner.width / 2.0;
        double cy = inner.y + inner.height / 2.0;
        return cx > outer.x && cx < outer.x + outer.width
                && cy > outer.y && cy < outer.y + outer.height;
    }

    private double intersectionOverUnion(Rect a, Rect b) {
        int left = Math.max(a.x, b.x);
        int top = Math.max(a.y, b.y);
        int right = Math.min(a.x + a.width, b.x + b.width);
        int bottom = Math.min(a.y + a.height, b.y + b.height);
        if (right <= left || bottom <= top) {
            return 0.0;
        }
        long intersection = (long) (right - left) * (bottom - top);
        long areaA = (long) a.width * a.height;
        long areaB = (long) b.width * b.height;
        return intersection / (double) Math.max(1L, areaA + areaB - intersection);
    }

    private byte[] png(Mat mat) {
        MatOfByte encoded = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", mat, encoded)) {
                throw new AssistantPlanningException("OpenCV no pudo serializar diagnostico de regiones UML.");
            }
            return encoded.toArray();
        } finally {
            encoded.release();
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (OpenCvUmlClassRegionDetector.class) {
            if (!loaded) {
                OpenCV.loadLocally();
                loaded = true;
            }
        }
    }

    /** Internal, best-effort evidence trace; it deliberately does not affect detection. */
    private static final class DetectionDiagnostics {
        private final String filename;
        private final List<String> companions = new ArrayList<>();
        private final List<String> houghLines = new ArrayList<>();
        private final List<String> houghHypotheses = new ArrayList<>();
        private final List<String> projectedCorners = new ArrayList<>();
        private List<FinalCandidate> hypotheses = List.of();
        private List<FinalCandidate> topCandidates = List.of();

        private DetectionDiagnostics(String filename) {
            this.filename = filename;
        }

        void companion(Rect seed, Rect state, Rect companion, String decision, Double score) {
            companions.add("{\"seed\":" + box(seed) + ",\"state\":" + box(state)
                    + ",\"companion\":" + box(companion) + ",\"decision\":\"" + decision
                    + "\",\"score\":" + (score == null ? "null" : score) + "}");
        }

        void maximalChain(Rect seed, Rect state, int sourceCount) {
            companions.add("{\"seed\":" + box(seed) + ",\"state\":" + box(state)
                    + ",\"decision\":\"fixed-point-maximal\",\"sourceCount\":" + sourceCount + "}");
        }

        void houghHypothesis(
                Rect seed,
                Rect union,
                int side,
                Line closing,
                double closingCoord,
                int houghContinuationCount,
                int binaryBridgeContinuationCount
        ) {
            houghHypotheses.add("{\"hypothesisId\":\"H" + (houghHypotheses.size() + 1)
                    + "\",\"seed\":" + box(seed)
                    + ",\"union\":" + box(union) + ",\"side\":" + side + ",\"closing\":["
                    + closing.x1() + "," + closing.y1() + "," + closing.x2() + "," + closing.y2()
                    + "],\"closingCoordAtUnionMidpoint\":" + closingCoord
                    + ",\"projectedCornerContinuations\":"
                    + (houghContinuationCount + binaryBridgeContinuationCount)
                    + ",\"houghProjectedCornerContinuations\":" + houghContinuationCount
                    + ",\"binaryProjectedCornerBridges\":" + binaryBridgeContinuationCount + "}");
        }

        void projectedCorner(Rect seed, Line closing, int side, double corner, String evidence) {
            projectedCorners.add("{\"seed\":" + box(seed) + ",\"side\":" + side
                    + ",\"closing\":[" + closing.x1() + "," + closing.y1() + "," + closing.x2() + ","
                    + closing.y2() + "],\"corner\":" + corner + ",\"evidence\":\"" + evidence + "\"}");
        }

        void hough(String context, Rect seed, Rect roi, int side, Line line) {
            houghLines.add("{\"context\":\"" + context + "\",\"seed\":" + box(seed)
                    + ",\"roi\":" + box(roi) + ",\"side\":" + side + ",\"line\":["
                    + line.x1() + "," + line.y1() + "," + line.x2() + "," + line.y2() + "]}");
        }

        void topCandidates(List<FinalCandidate> candidates) {
            topCandidates = candidates;
        }

        void hypotheses(List<FinalCandidate> candidates) {
            hypotheses = candidates;
        }

        void write(List<FinalCandidate> finalCandidates) {
            Path report = Path.of("build", "reports", "opencv-class-region-detector",
                    filename.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
            List<String> candidates = candidateJson(topCandidates);
            List<String> ranked = candidateJson(hypotheses);
            List<String> finals = new ArrayList<>();
            for (int index = 0; index < finalCandidates.size(); index++) {
                FinalCandidate candidate = finalCandidates.get(index);
                finals.add("{\"geometryId\":\"B" + (index + 1) + "\",\"reconstruction\":\""
                        + candidate.reconstruction() + "\",\"seed\":" + box(candidate.seed().box())
                        + ",\"box\":" + box(candidate.box()) + ",\"sourceCount\":"
                        + candidate.sourceCount() + ",\"maximalMerge\":" + candidate.maximalMerge()
                        + ",\"evidenceComplete\":" + candidate.evidenceComplete()
                        + ",\"selectionScore\":" + candidate.selectionScore() + "}");
            }
            String json = "{\n  \"input\": \"" + escape(filename) + "\",\n  \"topCandidates\": [\n"
                    + String.join(",\n", candidates) + "\n  ],\n  \"rankedHypotheses\": [\n"
                    + String.join(",\n", ranked) + "\n  ],\n  \"finals\": [\n" + String.join(",\n", finals)
                    + "\n  ],\n  \"companionEvaluations\": [\n" + String.join(",\n", companions)
                    + "\n  ],\n  \"houghHypotheses\": [\n" + String.join(",\n", houghHypotheses)
                    + "\n  ],\n  \"projectedCornerEvidence\": [\n" + String.join(",\n", projectedCorners)
                    + "\n  ],\n  \"rawHoughLines\": [\n" + String.join(",\n", houghLines) + "\n  ]\n}";
            try {
                Files.createDirectories(report.getParent());
                Files.writeString(report, json);
            } catch (IOException ignored) {
                // Diagnostic persistence must never alter detector availability.
            }
        }

        private static String box(Rect box) {
            return "[" + box.x + "," + box.y + "," + box.width + "," + box.height + "]";
        }

        private static List<String> candidateJson(List<FinalCandidate> candidates) {
            List<String> result = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                FinalCandidate candidate = candidates.get(index);
                result.add("    {\"rank\":" + (index + 1) + ",\"reconstruction\":\""
                        + candidate.reconstruction() + "\",\"evidenceComplete\":" + candidate.evidenceComplete()
                        + ",\"seed\":" + box(candidate.seed().box()) + ",\"box\":" + box(candidate.box())
                        + ",\"score\":" + candidate.selectionScore() + ",\"sourceCount\":"
                        + candidate.sourceCount() + ",\"maximalMerge\":" + candidate.maximalMerge() + "}");
            }
            return result;
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private record Candidate(Rect box, double rectangularity, double area) {
    }

    private record FinalCandidate(
            Candidate seed,
            Rect box,
            Reconstruction reconstruction,
            Candidate companion,
            double score,
            int sourceCount,
            boolean maximalMerge
    ) {
        static FinalCandidate direct(Candidate seed, boolean complete) {
            return new FinalCandidate(seed, seed.box(), complete ? Reconstruction.COMPLETE : Reconstruction.DIRECT,
                    null, 0.0, 1, true);
        }

        static FinalCandidate companion(Candidate seed, ChainState chain, boolean maximalMerge) {
            return new FinalCandidate(seed, chain.box(), Reconstruction.COMPANION,
                    chain.consumed().getLast(), chain.score(), chain.consumed().size(), maximalMerge);
        }

        static FinalCandidate hough(Candidate seed, Rect box, double score) {
            return new FinalCandidate(seed, box, Reconstruction.HOUGH, null, score, 1, true);
        }

        double confidence() {
            double value = switch (reconstruction) {
                case DIRECT, COMPLETE -> 0.45 + 0.55 * seed.rectangularity();
                case COMPANION -> 0.40 + 0.20 * seed.rectangularity()
                        + 0.15 * companion.rectangularity() + 0.25 * score;
                case HOUGH -> 0.40 + 0.25 * seed.rectangularity() + 0.35 * score;
            };
            return Math.max(0.0, Math.min(1.0, value));
        }

        double selectionScore() {
            return switch (reconstruction) {
                case COMPLETE -> 1.2 + seed.rectangularity();
                case COMPANION -> 2.0 + score + seed.rectangularity();
                case HOUGH -> 1.75 + score + seed.rectangularity();
                case DIRECT -> seed.rectangularity();
            };
        }

        boolean evidenceComplete() {
            return reconstruction != Reconstruction.DIRECT
                    && (reconstruction != Reconstruction.COMPANION || (sourceCount > 1 && maximalMerge));
        }
    }

    private record CompanionMatch(Candidate companion, Rect union, int side, double score) {
    }

    private record HoughMatch(Line line, int side, double score, double closingCoord, int continuationCount) {
    }

    private record CornerContinuations(int hough, int binaryBridge) {
        int total() {
            return hough + binaryBridge;
        }
    }

    private record ChainState(Rect box, List<Candidate> consumed, double score) {
    }

    private record Line(double x1, double y1, double x2, double y2) {
    }

    private enum Reconstruction {
        DIRECT,
        COMPLETE,
        COMPANION,
        HOUGH
    }

    private enum Axis {
        VERTICAL,
        HORIZONTAL;

        static Axis of(Rect box) {
            return box.height >= box.width ? VERTICAL : HORIZONTAL;
        }

        Axis other() {
            return this == VERTICAL ? HORIZONTAL : VERTICAL;
        }

        int longSide(Rect box) {
            return this == VERTICAL ? box.height : box.width;
        }

        int shortSide(Rect box) {
            return this == VERTICAL ? box.width : box.height;
        }

        int longStart(Rect box) {
            return this == VERTICAL ? box.y : box.x;
        }

        int longEnd(Rect box) {
            return longStart(box) + longSide(box);
        }

        int shortStart(Rect box) {
            return this == VERTICAL ? box.x : box.y;
        }

        int shortEnd(Rect box) {
            return shortStart(box) + shortSide(box);
        }

        double longCoordinate(double x, double y) {
            return this == VERTICAL ? y : x;
        }

        double shortCoordinate(Line line) {
            return this == VERTICAL ? line.x1() : line.y1();
        }

        double shortCoordinateEnd(Line line) {
            return this == VERTICAL ? line.x2() : line.y2();
        }

        double perpendicularCenter(Line line) {
            return (shortCoordinate(line) + shortCoordinateEnd(line)) / 2.0;
        }

        double shortAtLongCoordinate(Line line, double longCoordinate) {
            double startLong = longCoordinate(line.x1(), line.y1());
            double endLong = longCoordinate(line.x2(), line.y2());
            if (Math.abs(endLong - startLong) < 0.0001) {
                return perpendicularCenter(line);
            }
            double ratio = (longCoordinate - startLong) / (endLong - startLong);
            return shortCoordinate(line) + ratio * (shortCoordinateEnd(line) - shortCoordinate(line));
        }

        double longAtShortCoordinate(Line line, double shortCoordinate) {
            double startShort = shortCoordinate(line);
            double endShort = shortCoordinateEnd(line);
            if (Math.abs(endShort - startShort) < 0.0001) {
                return Double.NaN;
            }
            double ratio = (shortCoordinate - startShort) / (endShort - startShort);
            return longCoordinate(line.x1(), line.y1())
                    + ratio * (longCoordinate(line.x2(), line.y2()) - longCoordinate(line.x1(), line.y1()));
        }

        double[] longCorners(Rect box) {
            return new double[]{longStart(box), longEnd(box)};
        }
    }
}
