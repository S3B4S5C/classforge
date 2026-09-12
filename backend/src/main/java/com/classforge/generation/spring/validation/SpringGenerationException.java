package com.classforge.generation.spring.validation;

import java.util.List;

public class SpringGenerationException extends RuntimeException {
    private final List<SpringGenerationDiagnostic> diagnostics;
    public SpringGenerationException(List<SpringGenerationDiagnostic> diagnostics) {
        super("Spring generation cannot be planned: " + diagnostics.size() + " diagnostic(s)");
        this.diagnostics = List.copyOf(diagnostics);
    }
    public List<SpringGenerationDiagnostic> diagnostics() { return diagnostics; }
}
