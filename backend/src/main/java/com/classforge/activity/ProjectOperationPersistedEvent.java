package com.classforge.activity;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.ProjectDocument;
import java.time.Instant;
import java.util.UUID;

/** Published synchronously inside the document transaction, after successful validation. */
public record ProjectOperationPersistedEvent(
        UUID projectId, UUID operationId, UUID actorUserId, String actorDisplayName,
        long revisionBefore, long revisionAfter, ActivitySource source,
        UmlCommandPayload command, ProjectDocument beforeDocument,
        ProjectDocument afterDocument, Instant occurredAt) { }
