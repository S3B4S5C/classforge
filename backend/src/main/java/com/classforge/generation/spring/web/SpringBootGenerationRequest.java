package com.classforge.generation.spring.web;

import jakarta.validation.constraints.NotBlank;

public record SpringBootGenerationRequest(
        long baseRevision,
        @NotBlank String artifactName,
        @NotBlank String basePackage,
        Boolean useFirstAttributeAsIdentifier
) {
    public boolean firstAttributeIdentifierFallbackEnabled() {
        return Boolean.TRUE.equals(useFirstAttributeAsIdentifier);
    }
}
