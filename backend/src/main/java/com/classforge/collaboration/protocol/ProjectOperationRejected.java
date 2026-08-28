package com.classforge.collaboration.protocol;

import java.time.Instant;
import java.util.UUID;

public record ProjectOperationRejected(
        String type,
        UUID operationId,
        UUID projectId,
        UUID clientId,
        String code,
        String message,
        Long currentRevision,
        Instant rejectedAt
) {
    public static final String TYPE = "OPERATION_REJECTED";

    public ProjectOperationRejected(
            UUID operationId,
            UUID projectId,
            UUID clientId,
            String code,
            String message,
            Long currentRevision
    ) {
        this(
                TYPE,
                operationId,
                projectId,
                clientId,
                code,
                message,
                currentRevision,
                Instant.now()
        );
    }
}