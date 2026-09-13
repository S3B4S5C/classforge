package com.classforge.generation.spring.web;

import java.util.List;

public record SpringBootGenerationErrorResponse(
        String error,
        String message,
        List<SpringBootGenerationDiagnosticResponse> diagnostics,
        List<SpringBootGenerationPrimaryKeyFallbackResponse> primaryKeyFallbacks
) {
    public SpringBootGenerationErrorResponse {
        diagnostics = List.copyOf(diagnostics);
        primaryKeyFallbacks = List.copyOf(primaryKeyFallbacks);
    }
}
