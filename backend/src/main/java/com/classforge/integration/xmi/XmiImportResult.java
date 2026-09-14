package com.classforge.integration.xmi;

import com.classforge.project.domain.document.ProjectDocument;

import java.util.List;

public record XmiImportResult(
        ProjectDocument document,
        int packageCount,
        int classCount,
        int attributeCount,
        int relationshipCount,
        List<XmiDiagnostic> diagnostics
) {
    public XmiImportResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
