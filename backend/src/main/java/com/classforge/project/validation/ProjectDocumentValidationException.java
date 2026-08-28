package com.classforge.project.validation;

import java.util.List;

public class ProjectDocumentValidationException extends RuntimeException {
    private final List<ValidationViolation> violations;

    public ProjectDocumentValidationException(List<ValidationViolation> violations) {
        super("The project document contains validation errors");
        this.violations = List.copyOf(violations);
    }

    public List<ValidationViolation> getViolations() {
        return violations;
    }
}