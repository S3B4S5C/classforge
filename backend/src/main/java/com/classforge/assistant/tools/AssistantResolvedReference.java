package com.classforge.assistant.tools;

import java.util.UUID;

public record AssistantResolvedReference(
        ReferenceKind kind,
        String observed,
        String canonicalName,
        UUID elementId,
        double confidence,
        String scope
) {
    public enum ReferenceKind {
        CLASS,
        ATTRIBUTE,
        RELATIONSHIP
    }
}
