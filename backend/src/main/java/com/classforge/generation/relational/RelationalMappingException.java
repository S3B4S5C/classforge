package com.classforge.generation.relational;

import java.util.List;

public class RelationalMappingException extends RuntimeException {
    private final List<RelationalMappingDiagnostic> diagnostics;
    public RelationalMappingException(List<RelationalMappingDiagnostic> diagnostics) {
        super("Relational mapping cannot be generated: " + diagnostics.size() + " diagnostic(s)");
        this.diagnostics = List.copyOf(diagnostics);
    }
    public List<RelationalMappingDiagnostic> diagnostics() { return diagnostics; }
}
