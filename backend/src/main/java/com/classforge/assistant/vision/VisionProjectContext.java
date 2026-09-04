package com.classforge.assistant.vision;

import java.util.List;
import java.util.UUID;

public record VisionProjectContext(
        UUID projectId,
        long revision,
        List<VisionExistingClassContext> classes
) {
}
