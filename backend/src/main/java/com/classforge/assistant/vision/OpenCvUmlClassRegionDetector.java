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

            List<Candidate> candidates = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect box = Imgproc.boundingRect(contour);
                double boxArea = (double) box.width * box.height;
                if (box.width < minWidth || box.height < minHeight
                        || boxArea < minArea || boxArea > maxArea) {
                    continue;
                }
                double aspect = box.width / (double) Math.max(1, box.height);
                if (aspect < 0.24 || aspect > 4.2) {
                    continue;
                }
                double contourArea = Math.abs(Imgproc.contourArea(contour));
                double rectangularity = contourArea / Math.max(1.0, boxArea);
                if (rectangularity < 0.62) {
                    continue;
                }
                MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                try {
                    double perimeter = Imgproc.arcLength(curve, true);
                    Imgproc.approxPolyDP(curve, approx, Math.max(2.0, perimeter * 0.02), true);
                    int vertices = approx.toArray().length;
                    if (vertices < 4 || vertices > 12) {
                        continue;
                    }
                } finally {
                    curve.release();
                    approx.release();
                }
                candidates.add(new Candidate(box, rectangularity, boxArea));
            }

            // Prefer larger outer rectangles. Compartments and nested text boxes are
            // naturally smaller and are suppressed by overlap/containment below.
            candidates.sort(Comparator
                    .comparingDouble(Candidate::area).reversed()
                    .thenComparing(Comparator.comparingDouble(Candidate::rectangularity).reversed()));

            List<Candidate> selected = new ArrayList<>();
            for (Candidate candidate : candidates) {
                boolean overlaps = false;
                for (Candidate existing : selected) {
                    if (intersectionOverUnion(candidate.box(), existing.box()) > 0.18
                            || centerInside(candidate.box(), existing.box())
                            || centerInside(existing.box(), candidate.box())) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    selected.add(candidate);
                    if (selected.size() == expectedClassCount) {
                        break;
                    }
                }
            }

            if (selected.size() != expectedClassCount) {
                throw new AssistantPlanningException(
                        "OpenCV detecto " + selected.size() + " cajas UML plausibles; se esperaban "
                                + expectedClassCount + ". Se rechaza el modo hibrido sin completar cajas por proximidad."
                );
            }

            // Stable spatial ordering makes B ids deterministic between runs. The VLM
            // still maps Bx -> classRef, so no semantic assumption is made here.
            selected.sort(Comparator
                    .comparingInt((Candidate c) -> c.box().y)
                    .thenComparingInt(c -> c.box().x));

            List<VisionGeometryClassRegion> regions = new ArrayList<>();
            int index = 1;
            for (Candidate candidate : selected) {
                Rect box = candidate.box();
                double confidence = Math.min(1.0, Math.max(0.0, 0.45 + candidate.rectangularity() * 0.55));
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

    private record Candidate(Rect box, double rectangularity, double area) {
    }
}
