package com.classforge.project.web;

public record ProjectRevisionConflictResponse(
        String error,
        String message,
        long requestedRevision,
        long currentRevision
) {
}