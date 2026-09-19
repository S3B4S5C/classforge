package com.classforge.project.validation;

import java.util.ArrayList;
import java.util.UUID;

final class ProjectValidationCollector {
    private final ArrayList<ValidationDiagnostic> diagnostics = new ArrayList<>();

    void error(String field, String code, UUID elementId, String message) {
        add(ValidationSeverity.ERROR, field, code, elementId, message);
    }

    void warning(String field, String code, UUID elementId, String message) {
        add(ValidationSeverity.WARNING, field, code, elementId, message);
    }

    void info(String field, String code, UUID elementId, String message) {
        add(ValidationSeverity.INFO, field, code, elementId, message);
    }

    private void add(ValidationSeverity severity, String field, String code, UUID elementId, String message) {
        diagnostics.add(new ValidationDiagnostic(severity, code, field, elementId, message));
    }

    ProjectDocumentValidationReport report() {
        return ProjectDocumentValidationReport.from(diagnostics);
    }
}
