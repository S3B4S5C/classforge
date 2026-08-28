package com.classforge.collaboration.presence;

import java.util.UUID;

public record PresenceActor(
        UUID id,
        String displayName
) {
}