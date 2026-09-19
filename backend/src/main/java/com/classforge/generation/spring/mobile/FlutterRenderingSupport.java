package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterRenderingSupport {

    DomainManifestPlan.Entity authEntity(DomainManifestPlan manifest) {
        return manifest.entities().stream().filter(e -> e.id().equals(manifest.authentication().entityId())).findFirst().orElseThrow();
    }

    String snake(String value) {
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('-', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "_");
    }

    String dartPackage(String artifactName) {
        String result = artifactName.toLowerCase(Locale.ROOT).replace('-', '_').replaceAll("[^a-z0-9_]", "_");
        if (result.isBlank() || !Character.isLetter(result.charAt(0))) result = "app_" + result;
        return result;
    }

    String normalizeColor(String value) {
        String color = value == null ? "#2563EB" : value.trim().toUpperCase(Locale.ROOT);
        if (!color.matches("^#[0-9A-F]{6}$")) throw new IllegalArgumentException("primary color must be #RRGGBB");
        return color;
    }
}
