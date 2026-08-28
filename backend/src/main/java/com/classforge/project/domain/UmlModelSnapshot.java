package com.classforge.project.domain;

import java.util.List;
import java.util.Map;

public record UmlModelSnapshot(
        String schemaVersion,
        List<Map<String, Object>> elements
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public UmlModelSnapshot {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    public static UmlModelSnapshot empty() {
        return new UmlModelSnapshot(CURRENT_SCHEMA_VERSION, List.of());
    }
}