package com.classforge.collaboration.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceParticipant(
        UUID clientId,
        PresenceActor actor,
        PresenceSelection selectedElement,
        PresenceCursor cursor,
        Instant joinedAt
) {
}