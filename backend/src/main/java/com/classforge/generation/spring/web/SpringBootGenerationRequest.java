package com.classforge.generation.spring.web;

import com.classforge.generation.spring.application.SpringBootGenerationMode;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record SpringBootGenerationRequest(
        long baseRevision,
        @NotBlank String artifactName,
        @NotBlank String basePackage,
        Boolean useFirstAttributeAsIdentifier,
        SpringBootGenerationMode mode,
        UUID authClassId,
        UUID usernameAttributeId,
        UUID passwordAttributeId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor
) {
    public SpringBootGenerationRequest(
            long baseRevision,
            String artifactName,
            String basePackage,
            Boolean useFirstAttributeAsIdentifier,
            SpringBootGenerationMode mode,
            UUID authClassId,
            UUID usernameAttributeId,
            UUID passwordAttributeId
    ) {
        this(
                baseRevision,
                artifactName,
                basePackage,
                useFirstAttributeAsIdentifier,
                mode,
                authClassId,
                usernameAttributeId,
                passwordAttributeId,
                null
        );
    }

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

    public String effectivePrimaryColor() {
        return primaryColor == null || primaryColor.isBlank()
                ? SpringBootGenerationOptions.DEFAULT_PRIMARY_COLOR
                : primaryColor.toUpperCase();
    }
}
