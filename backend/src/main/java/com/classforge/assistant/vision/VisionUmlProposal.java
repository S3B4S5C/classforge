package com.classforge.assistant.vision;

import java.util.List;

public record VisionUmlProposal(
        String summary,
        List<VisionClassProposal> classes,
        List<VisionRelationshipProposal> relationships,
        List<String> warnings,
        Double confidence
) {
    public List<VisionClassProposal> safeClasses() {
        return classes == null ? List.of() : List.copyOf(classes);
    }

    public List<VisionRelationshipProposal> safeRelationships() {
        return relationships == null ? List.of() : List.copyOf(relationships);
    }

    public List<String> safeWarnings() {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
