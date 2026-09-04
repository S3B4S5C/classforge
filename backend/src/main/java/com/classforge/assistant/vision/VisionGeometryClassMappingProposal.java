package com.classforge.assistant.vision;

import java.util.List;

public record VisionGeometryClassMappingProposal(
        List<VisionGeometryClassMapping> mappings,
        List<String> warnings,
        Double confidence
) {
    public List<VisionGeometryClassMapping> safeMappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }

    public List<String> safeWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
