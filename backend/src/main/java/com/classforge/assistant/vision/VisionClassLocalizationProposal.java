package com.classforge.assistant.vision;

import java.util.List;

public record VisionClassLocalizationProposal(
        List<VisionClassLocalization> mappings,
        List<String> warnings,
        Double confidence
) {
    public List<VisionClassLocalization> safeMappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }

    public List<String> safeWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
