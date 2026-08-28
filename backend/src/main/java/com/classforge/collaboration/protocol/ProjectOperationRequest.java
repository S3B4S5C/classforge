package com.classforge.collaboration.protocol;

import java.util.UUID;

public record ProjectOperationRequest(
        UUID operationId,
        UUID projectId,
        UUID clientId,
        long baseRevision,
        UmlCommandPayload command
) {
}