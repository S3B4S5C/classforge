package com.classforge.project.web;

import com.classforge.project.domain.document.ProjectDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ValidateProjectDocumentRequest(
        @Valid
        @NotNull(message = "document is required")
        ProjectDocument document
) {
}