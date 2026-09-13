package com.classforge.generation.spring.api;

import java.util.UUID;

public record SpringApiGenerationDiagnostic(
        SpringApiGenerationDiagnosticCode code,
        UUID elementId,
        String path,
        String message
) { }
