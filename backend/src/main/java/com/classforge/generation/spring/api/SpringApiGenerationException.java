package com.classforge.generation.spring.api;

import java.util.List;

public class SpringApiGenerationException extends RuntimeException {
    private final List<SpringApiGenerationDiagnostic> diagnostics;

    public SpringApiGenerationException(List<SpringApiGenerationDiagnostic> diagnostics) {
        super(diagnostics == null || diagnostics.isEmpty()
                ? "Spring CRUD/API generation configuration is invalid."
                : diagnostics.getFirst().message());
        this.diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<SpringApiGenerationDiagnostic> diagnostics() {
        return diagnostics;
    }
}
