package com.classforge.project.domain.document;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record DiagramLayout(
        Map<UUID, DiagramNodeLayout> nodes
) {
    public DiagramLayout {
        nodes = nodes == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(nodes));
    }

    public static DiagramLayout empty() {
        return new DiagramLayout(Map.of());
    }
}