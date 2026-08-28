package com.classforge.common.web;

import com.classforge.project.validation.ValidationViolation;

import java.util.List;

public record ApiValidationErrorResponse(
        String error,
        String message,
        List<ValidationViolation> violations
) {
}