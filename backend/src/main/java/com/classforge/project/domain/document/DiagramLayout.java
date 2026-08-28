package com.classforge.project.domain.document;

import java.util.Map;

public record DiagramLayout(
        Map<String, Map<String, Object>> nodes
) {

    public DiagramLayout {
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public static DiagramLayout empty() {
        return new DiagramLayout(Map.of());
    }
}