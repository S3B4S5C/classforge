package com.classforge.assistant.vision;

public interface UmlClassRegionDetector {
    UmlClassRegionDetection detect(VisionNormalizedImage image, int expectedClassCount);
}
