package com.classforge.project.web;

import com.classforge.activity.ActivitySource;
import java.util.UUID;
import com.classforge.project.domain.document.ProjectDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveProjectDocumentRequest(
        @Min(value = 0, message = "baseRevision cannot be negative")
        long baseRevision,

        @Valid
        @NotNull(message = "document is required")
        ProjectDocument document,
        ActivitySource source,
        UUID operationId
) {
    public SaveProjectDocumentRequest {
        source = ActivitySource.orManual(source);
    }
    public SaveProjectDocumentRequest(long baseRevision, ProjectDocument document) {
        this(baseRevision, document, ActivitySource.MANUAL, null);
    }
}
