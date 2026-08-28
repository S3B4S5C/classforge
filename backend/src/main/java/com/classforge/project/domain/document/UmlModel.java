package com.classforge.project.domain.document;

import java.util.List;
import java.util.Map;

public record UmlModel(
        List<Map<String, Object>> classes,
        List<Map<String, Object>> relationships
) {

    public UmlModel {
        classes = classes == null ? List.of() : List.copyOf(classes);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }

    public static UmlModel empty() {
        return new UmlModel(List.of(), List.of());
    }
}