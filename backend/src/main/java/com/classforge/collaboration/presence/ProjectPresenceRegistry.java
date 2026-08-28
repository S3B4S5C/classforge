package com.classforge.collaboration.presence;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ProjectPresenceRegistry {

    private final ConcurrentMap<String, PresenceSession>
            sessions =
            new ConcurrentHashMap<>();

    public PresenceParticipant join(
            UUID projectId,
            String sessionId,
            UUID clientId,
            PresenceActor actor
    ) {
        require(
                projectId != null,
                "projectId is required"
        );

        require(
                sessionId != null
                        && !sessionId.isBlank(),
                "STOMP sessionId is required"
        );

        require(
                clientId != null,
                "clientId is required"
        );

        require(
                actor != null
                        && actor.id() != null
                        && actor.displayName() != null
                        && !actor.displayName().isBlank(),
                "actor is required"
        );

        PresenceParticipant participant =
                new PresenceParticipant(
                        clientId,
                        actor,
                        null,
                        null,
                        Instant.now()
                );

        sessions.put(
                sessionId,
                new PresenceSession(
                        projectId,
                        sessionId,
                        participant
                )
        );

        return participant;
    }

    public PresenceParticipant select(
            UUID projectId,
            String sessionId,
            UUID clientId,
            PresenceSelection selection
    ) {
        PresenceSession session =
                requireSession(
                        projectId,
                        sessionId,
                        clientId
                );

        if (selection != null) {
            require(
                    selection.elementId() != null
                            && selection.elementType() != null,
                    "selectedElement requires elementId and elementType"
            );
        }

        PresenceParticipant updated =
                new PresenceParticipant(
                        session.participant()
                                .clientId(),
                        session.participant()
                                .actor(),
                        selection,
                        session.participant()
                                .cursor(),
                        session.participant()
                                .joinedAt()
                );

        sessions.put(
                sessionId,
                session.withParticipant(
                        updated
                )
        );

        return updated;
    }

    public PresenceParticipant moveCursor(
            UUID projectId,
            String sessionId,
            UUID clientId,
            PresenceCursor cursor
    ) {
        PresenceSession session =
                requireSession(
                        projectId,
                        sessionId,
                        clientId
                );

        requireCursor(cursor);

        PresenceParticipant updated =
                new PresenceParticipant(
                        session.participant()
                                .clientId(),
                        session.participant()
                                .actor(),
                        session.participant()
                                .selectedElement(),
                        cursor,
                        session.participant()
                                .joinedAt()
                );

        sessions.put(
                sessionId,
                session.withParticipant(
                        updated
                )
        );

        return updated;
    }

    public RemovedPresence leave(
            UUID projectId,
            String sessionId,
            UUID clientId
    ) {
        PresenceSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }

        require(
                session.projectId()
                        .equals(projectId),
                "Presence session belongs to another project"
        );

        require(
                session.participant()
                        .clientId()
                        .equals(clientId),
                "clientId does not match this STOMP session"
        );

        sessions.remove(
                sessionId,
                session
        );

        return new RemovedPresence(
                session.projectId(),
                session.participant()
        );
    }

    public RemovedPresence disconnect(
            String sessionId
    ) {
        if (
                sessionId == null
                        || sessionId.isBlank()
        ) {
            return null;
        }

        PresenceSession removed =
                sessions.remove(
                        sessionId
                );

        if (removed == null) {
            return null;
        }

        return new RemovedPresence(
                removed.projectId(),
                removed.participant()
        );
    }

    public List<PresenceParticipant> snapshot(
            UUID projectId
    ) {
        return sessions
                .values()
                .stream()
                .filter(
                        session ->
                                session.projectId()
                                        .equals(projectId)
                )
                .map(
                        PresenceSession::participant
                )
                .sorted(
                        Comparator
                                .comparing(
                                        (PresenceParticipant participant) ->
                                                participant.actor()
                                                        .displayName(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        (PresenceParticipant participant) ->
                                                participant.clientId()
                                                        .toString()
                                )
                )
                .toList();
    }

    public int sessionCount() {
        return sessions.size();
    }

    private PresenceSession requireSession(
            UUID projectId,
            String sessionId,
            UUID clientId
    ) {
        PresenceSession session =
                sessions.get(sessionId);

        require(
                session != null,
                "USER_JOINED must be sent before presence updates"
        );

        require(
                session.projectId()
                        .equals(projectId),
                "Presence session belongs to another project"
        );

        require(
                session.participant()
                        .clientId()
                        .equals(clientId),
                "clientId does not match this STOMP session"
        );

        return session;
    }

    private void requireCursor(
            PresenceCursor cursor
    ) {
        require(
                cursor != null
                        && cursor.x() != null
                        && cursor.y() != null
                        && Double.isFinite(
                                cursor.x()
                        )
                        && Double.isFinite(
                                cursor.y()
                        ),
                "Cursor coordinates must be finite numbers"
        );
    }

    private void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new PresenceRejectedException(
                    message
            );
        }
    }

    private record PresenceSession(
            UUID projectId,
            String sessionId,
            PresenceParticipant participant
    ) {
        PresenceSession withParticipant(
                PresenceParticipant participant
        ) {
            return new PresenceSession(
                    projectId,
                    sessionId,
                    participant
            );
        }
    }

    public record RemovedPresence(
            UUID projectId,
            PresenceParticipant participant
    ) {
    }
}