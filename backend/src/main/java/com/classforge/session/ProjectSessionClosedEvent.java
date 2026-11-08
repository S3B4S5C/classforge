package com.classforge.session;

/** Transactional domain event available for the next notification stage. */
public record ProjectSessionClosedEvent(ProjectSession session, java.util.UUID actorUserId) {
    public ProjectSessionClosedEvent(ProjectSession session) { this(session, null); }
}
