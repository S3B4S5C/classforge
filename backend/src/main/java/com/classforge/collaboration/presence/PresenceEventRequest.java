package com.classforge.collaboration.presence;

import java.util.UUID;

public record PresenceEventRequest(
        UUID eventId,
        UUID clientId,
        PresenceEventType type,
        PresenceSelection selectedElement,
        PresenceCursor cursor
) {
}