package com.classforge.integration.xmi;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record XmiImportApplyRequest(
        @NotNull UUID previewToken
) {
}
