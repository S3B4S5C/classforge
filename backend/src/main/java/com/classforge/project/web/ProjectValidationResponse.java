package com.classforge.project.web;

import com.classforge.project.validation.ProjectDocumentValidationReport;
import com.classforge.project.validation.ValidationDiagnostic;

import java.util.List;

public record ProjectValidationResponse(
        boolean valid,
        int errors,
        int warnings,
        int infos,
        List<ValidationDiagnostic> diagnostics
) {

    public static ProjectValidationResponse from(
            ProjectDocumentValidationReport report
    ) {
        return new ProjectValidationResponse(
                report.valid(),
                report.errors(),
                report.warnings(),
                report.infos(),
                report.diagnostics()
        );
    }
}