package com.classforge.project.validation;

import java.util.List;

public record ProjectDocumentValidationReport(
        boolean valid,
        int errors,
        int warnings,
        int infos,
        List<ValidationDiagnostic> diagnostics
) {

    public ProjectDocumentValidationReport {
        diagnostics = diagnostics == null
                ? List.of()
                : List.copyOf(diagnostics);
    }

    public static ProjectDocumentValidationReport from(
            List<ValidationDiagnostic> diagnostics
    ) {
        List<ValidationDiagnostic> safeDiagnostics =
                diagnostics == null
                        ? List.of()
                        : List.copyOf(diagnostics);

        int errors = count(
                safeDiagnostics,
                ValidationSeverity.ERROR
        );

        return new ProjectDocumentValidationReport(
                errors == 0,
                errors,
                count(
                        safeDiagnostics,
                        ValidationSeverity.WARNING
                ),
                count(
                        safeDiagnostics,
                        ValidationSeverity.INFO
                ),
                safeDiagnostics
        );
    }

    private static int count(
            List<ValidationDiagnostic> diagnostics,
            ValidationSeverity severity
    ) {
        return Math.toIntExact(
                diagnostics.stream()
                        .filter(
                                diagnostic ->
                                        diagnostic.severity() == severity
                        )
                        .count()
        );
    }
}