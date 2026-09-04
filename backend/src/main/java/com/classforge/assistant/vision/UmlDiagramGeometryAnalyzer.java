package com.classforge.assistant.vision;

import java.util.List;

public interface UmlDiagramGeometryAnalyzer {

    UmlDiagramGeometry analyze(
            VisionNormalizedImage image,
            List<VisionGeometryClassRegion> regions
    );
}
