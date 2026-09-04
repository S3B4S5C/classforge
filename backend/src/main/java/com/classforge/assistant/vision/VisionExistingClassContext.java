package com.classforge.assistant.vision;

import java.util.List;
import java.util.UUID;

public record VisionExistingClassContext(
        UUID id,
        String name,
        List<String> attributes
) {
}
