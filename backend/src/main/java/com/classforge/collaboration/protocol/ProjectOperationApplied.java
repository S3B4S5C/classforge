package com.classforge.collaboration.protocol;

import java.time.Instant;
import java.util.UUID;

public record ProjectOperationApplied(
        String type,
        UUID operationId,
        UUID projectId,
        UUID clientId,
        long revision,
        UmlCommandPayload command,
        CollaborationActor actor,
        Instant appliedAt
) {
    public static final String TYPE = "OPERATION_APPLIED";

    public ProjectOperationApplied(
            UUID operationId,
            UUID projectId,
            UUID clientId,
            long revision,
            UmlCommandPayload command,
            CollaborationActor actor,
            Instant appliedAt
    ) {
        this(
                TYPE,
                operationId,
                projectId,
                clientId,
                revision,
                command,
                actor,
                appliedAt
        );
    }
}