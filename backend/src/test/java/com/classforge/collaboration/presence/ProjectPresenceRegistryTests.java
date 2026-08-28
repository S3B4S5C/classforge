package com.classforge.collaboration.presence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectPresenceRegistryTests {

    private final ProjectPresenceRegistry registry =
            new ProjectPresenceRegistry();

    @Test
    void joinSelectCursorAndDisconnectStayInMemory() {
        UUID projectId =
                UUID.randomUUID();

        UUID clientId =
                UUID.randomUUID();

        String sessionId =
                "session-a";

        PresenceParticipant joined =
                registry.join(
                        projectId,
                        sessionId,
                        clientId,
                        new PresenceActor(
                                UUID.randomUUID(),
                                "Ana"
                        )
                );

        assertEquals(
                1,
                registry.sessionCount()
        );

        assertEquals(
                clientId,
                joined.clientId()
        );

        PresenceParticipant selected =
                registry.select(
                        projectId,
                        sessionId,
                        clientId,
                        new PresenceSelection(
                                UUID.randomUUID(),
                                PresenceElementType.CLASS
                        )
                );

        assertEquals(
                PresenceElementType.CLASS,
                selected.selectedElement()
                        .elementType()
        );

        PresenceParticipant moved =
                registry.moveCursor(
                        projectId,
                        sessionId,
                        clientId,
                        new PresenceCursor(
                                120.5,
                                42.25
                        )
                );

        assertEquals(
                120.5,
                moved.cursor()
                        .x()
        );

        ProjectPresenceRegistry.RemovedPresence removed =
                registry.disconnect(
                        sessionId
                );

        assertEquals(
                clientId,
                removed.participant()
                        .clientId()
        );

        assertEquals(
                0,
                registry.sessionCount()
        );

        assertNull(
                registry.disconnect(
                        sessionId
                )
        );
    }
}