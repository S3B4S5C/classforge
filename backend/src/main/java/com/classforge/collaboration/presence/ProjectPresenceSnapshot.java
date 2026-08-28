package com.classforge.collaboration.presence;

import java.util.List;
import java.util.UUID;

public record ProjectPresenceSnapshot(
        String type,
        UUID projectId,
        List<PresenceParticipant> participants
) {
    public static final String TYPE =
            "PRESENCE_SNAPSHOT";

    public ProjectPresenceSnapshot(
            UUID projectId,
            List<PresenceParticipant> participants
    ) {
        this(
                TYPE,
                projectId,
                List.copyOf(participants)
        );
    }
}