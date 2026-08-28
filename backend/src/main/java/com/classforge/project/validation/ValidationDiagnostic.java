package com.classforge.project.validation;

import java.util.UUID;

public record ValidationDiagnostic(
        ValidationSeverity severity,
        String code,
        String field,
        UUID elementId,
        String message
) {
}