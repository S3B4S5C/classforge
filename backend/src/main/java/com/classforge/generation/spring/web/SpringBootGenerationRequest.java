package com.classforge.generation.spring.web;

import com.classforge.generation.spring.application.SpringBootGenerationMode;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SpringBootGenerationRequest(
        long baseRevision,
        @NotBlank String artifactName,
        @NotBlank String basePackage,
        Boolean useFirstAttributeAsIdentifier,
        SpringBootGenerationMode mode,
        UUID authClassId,
        UUID usernameAttributeId,
        UUID passwordAttributeId
) {
    public SpringBootGenerationRequest(
            long baseRevision,
            String artifactName,
            String basePackage,
            Boolean useFirstAttributeAsIdentifier
    ) {
        this(
                baseRevision,
                artifactName,
                basePackage,
                useFirstAttributeAsIdentifier,
                null,
                null,
                null,
                null
        );
    }

    public boolean firstAttributeIdentifierFallbackEnabled() {
        return Boolean.TRUE.equals(useFirstAttributeAsIdentifier);
    }

    public SpringBootGenerationMode effectiveMode() {
        return mode == null ? SpringBootGenerationMode.SIMPLE_CRUD : mode;
    }
}
