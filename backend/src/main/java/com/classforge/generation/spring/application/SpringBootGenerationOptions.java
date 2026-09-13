package com.classforge.generation.spring.application;

import java.util.UUID;

public record SpringBootGenerationOptions(
        boolean useFirstAttributeAsIdentifier,
        SpringBootGenerationMode mode,
        UUID authClassId,
        UUID usernameAttributeId,
        UUID passwordAttributeId,
        String primaryColor
) {
    public static final String DEFAULT_PRIMARY_COLOR = "#2563EB";

    public SpringBootGenerationOptions(boolean useFirstAttributeAsIdentifier) {
        this(useFirstAttributeAsIdentifier, null, null, null, null, DEFAULT_PRIMARY_COLOR);
    }

    public SpringBootGenerationOptions(
            boolean useFirstAttributeAsIdentifier,
            SpringBootGenerationMode mode,
            UUID authClassId,
            UUID usernameAttributeId,
            UUID passwordAttributeId
    ) {
        this(useFirstAttributeAsIdentifier, mode, authClassId, usernameAttributeId, passwordAttributeId, DEFAULT_PRIMARY_COLOR);
    }

    public SpringBootGenerationOptions {
        primaryColor = normalizePrimaryColor(primaryColor);
    }

    public static SpringBootGenerationOptions strict() {
        return new SpringBootGenerationOptions(false);
    }

    public static SpringBootGenerationOptions simpleCrud(boolean useFirstAttributeAsIdentifier) {
        return simpleCrud(useFirstAttributeAsIdentifier, DEFAULT_PRIMARY_COLOR);
    }

    public static SpringBootGenerationOptions simpleCrud(boolean useFirstAttributeAsIdentifier, String primaryColor) {
        return new SpringBootGenerationOptions(
                useFirstAttributeAsIdentifier,
                SpringBootGenerationMode.SIMPLE_CRUD,
                null,
                null,
                null,
                primaryColor
        );
    }

    public static SpringBootGenerationOptions authenticated(
            boolean useFirstAttributeAsIdentifier,
            UUID authClassId,
            UUID usernameAttributeId,
            UUID passwordAttributeId
    ) {
        return authenticated(
                useFirstAttributeAsIdentifier,
                authClassId,
                usernameAttributeId,
                passwordAttributeId,
                DEFAULT_PRIMARY_COLOR
        );
    }

    public static SpringBootGenerationOptions authenticated(
            boolean useFirstAttributeAsIdentifier,
            UUID authClassId,
            UUID usernameAttributeId,
            UUID passwordAttributeId,
            String primaryColor
    ) {
        return new SpringBootGenerationOptions(
                useFirstAttributeAsIdentifier,
                SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM,
                authClassId,
                usernameAttributeId,
                passwordAttributeId,
                primaryColor
        );
    }

    public boolean apiEnabled() {
        return mode != null;
    }

    public boolean authEnabled() {
        return mode == SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM;
    }

    private static String normalizePrimaryColor(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_PRIMARY_COLOR : value.trim().toUpperCase();
        if (!normalized.matches("^#[0-9A-F]{6}$")) {
            throw new IllegalArgumentException("primaryColor must use #RRGGBB format");
        }
        return normalized;
    }
}
