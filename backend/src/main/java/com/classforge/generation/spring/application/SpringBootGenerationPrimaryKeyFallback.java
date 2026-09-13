package com.classforge.generation.spring.application;

import java.util.UUID;

public record SpringBootGenerationPrimaryKeyFallback(
        UUID classId,
        String className,
        UUID attributeId,
        String attributeName
) { }
