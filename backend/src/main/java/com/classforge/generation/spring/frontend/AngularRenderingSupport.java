package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class AngularRenderingSupport {

    String kebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    String normalizeColor(String color) {
        String value = color == null ? "#2563EB" : color.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^#[0-9A-F]{6}$")) throw new IllegalArgumentException("primary color must be #RRGGBB");
        return value;
    }
}
