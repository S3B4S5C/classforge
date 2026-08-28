package com.classforge.project.web;

import com.classforge.project.domain.document.ProjectDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveProjectDocumentRequest(
        @Min(value = 0, message = "baseRevision cannot be negative")
        long baseRevision,

        @Valid
        @NotNull(message = "document is required")
        ProjectDocument document
) {
}