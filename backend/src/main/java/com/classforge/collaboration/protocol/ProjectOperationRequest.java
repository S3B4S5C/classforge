package com.classforge.collaboration.protocol;

import com.classforge.activity.ActivitySource;
import java.util.UUID;

public record ProjectOperationRequest(
        UUID operationId,
        UUID projectId,
        UUID clientId,
        long baseRevision,
        UmlCommandPayload command,
        ActivitySource source
) {
    public ProjectOperationRequest {
        source = ActivitySource.orManual(source);
    }
    public ProjectOperationRequest(UUID operationId, UUID projectId, UUID clientId,
            long baseRevision, UmlCommandPayload command) {
        this(operationId, projectId, clientId, baseRevision, command, ActivitySource.MANUAL);
    }
}
