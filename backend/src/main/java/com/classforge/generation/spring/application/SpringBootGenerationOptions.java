package com.classforge.generation.spring.application;

import java.util.UUID;

public record SpringBootGenerationOptions(
        boolean useFirstAttributeAsIdentifier,
        SpringBootGenerationMode mode,
        UUID authClassId,
        UUID usernameAttributeId,
        UUID passwordAttributeId
) {
    public SpringBootGenerationOptions(boolean useFirstAttributeAsIdentifier) {
        this(useFirstAttributeAsIdentifier, null, null, null, null);
    }

    public static SpringBootGenerationOptions strict() {
        return new SpringBootGenerationOptions(false);
    }

    public static SpringBootGenerationOptions simpleCrud(boolean useFirstAttributeAsIdentifier) {
        return new SpringBootGenerationOptions(
                useFirstAttributeAsIdentifier,
                SpringBootGenerationMode.SIMPLE_CRUD,
                null,
                null,
                null
        );
    }

    public static SpringBootGenerationOptions authenticated(
            boolean useFirstAttributeAsIdentifier,
            UUID authClassId,
            UUID usernameAttributeId,
            UUID passwordAttributeId
    ) {
        return new SpringBootGenerationOptions(
                useFirstAttributeAsIdentifier,
                SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM,
                authClassId,
                usernameAttributeId,
                passwordAttributeId
        );
    }

    public boolean apiEnabled() {
        return mode != null;
    }

    public boolean authEnabled() {
        return mode == SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM;
    }
}
