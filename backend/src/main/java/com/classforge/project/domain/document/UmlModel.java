package com.classforge.project.domain.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record UmlModel(
        List<UmlClass> classes,
        List<UmlRelationship> relationships
) {
    public UmlModel {
        classes = classes == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(classes));

        relationships = relationships == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(relationships));
    }

    public static UmlModel empty() {
        return new UmlModel(List.of(), List.of());
    }
}