package com.classforge.project.validation;

public record ValidationViolation(
        String field,
        String code,
        String message
) {
}