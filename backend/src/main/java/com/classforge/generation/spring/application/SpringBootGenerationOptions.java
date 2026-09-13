package com.classforge.generation.spring.application;

public record SpringBootGenerationOptions(boolean useFirstAttributeAsIdentifier) {
    public static SpringBootGenerationOptions strict() {
        return new SpringBootGenerationOptions(false);
    }
}
