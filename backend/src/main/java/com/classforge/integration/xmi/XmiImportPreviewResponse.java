package com.classforge.integration.xmi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record XmiImportPreviewResponse(
        UUID previewToken,
        long baseRevision,
        Instant expiresAt,
        int packageCount,
        int classCount,
        int attributeCount,
        int relationshipCount,
        List<XmiDiagnostic> diagnostics
) {
}
