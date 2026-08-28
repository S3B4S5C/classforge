package com.classforge.collaboration.presence;

import java.time.Instant;
import java.util.UUID;

public record ProjectPresenceEvent(
        String type,
        UUID projectId,
        PresenceParticipant participant,
        Instant occurredAt
) {
    public ProjectPresenceEvent(
            PresenceEventType type,
            UUID projectId,
            PresenceParticipant participant
    ) {
        this(
                type.name(),
                projectId,
                participant,
                Instant.now()
        );
    }
}