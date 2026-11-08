package com.classforge.session;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.UUID;

@Document("project_session_participants")
public class ProjectSessionParticipantEntity {
    @Id private UUID id;
    private UUID sessionId;
    private UUID userId;
    private String displayName;
    private long operations;
    private long changes;
    protected ProjectSessionParticipantEntity() { }
    public ProjectSessionParticipantEntity(ProjectSessionEntity session, UUID userId, String name) {
        this.id = UUID.randomUUID(); this.sessionId = session.getId(); this.userId = userId; this.displayName = name;
    }
    public void record(String name, int count) { displayName = name; operations++; changes += count; }
    public ProjectSession.Participant toParticipant() { return new ProjectSession.Participant(userId, displayName, operations, changes); }
}
