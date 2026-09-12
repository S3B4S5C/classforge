package com.classforge.generation.spring.generated;
import java.util.List;
public class GeneratedProjectException extends RuntimeException {
    private final List<GeneratedProjectDiagnostic> diagnostics;
    public GeneratedProjectException(List<GeneratedProjectDiagnostic> diagnostics) { super("Generated project is invalid: " + diagnostics.size() + " diagnostic(s)"); this.diagnostics = List.copyOf(diagnostics); }
    public GeneratedProjectException(List<GeneratedProjectDiagnostic> diagnostics, Throwable cause) { super("Generated project cannot be rendered", cause); this.diagnostics = List.copyOf(diagnostics); }
    public List<GeneratedProjectDiagnostic> diagnostics() { return diagnostics; }
}
