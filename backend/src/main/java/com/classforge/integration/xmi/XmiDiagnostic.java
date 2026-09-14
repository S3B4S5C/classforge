package com.classforge.integration.xmi;

public record XmiDiagnostic(
        XmiDiagnosticSeverity severity,
        String code,
        String message
) {
}
