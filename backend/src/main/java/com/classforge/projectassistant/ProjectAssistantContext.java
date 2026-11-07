package com.classforge.projectassistant;

import com.classforge.project.domain.document.ProjectDocument;
import java.util.List;
import java.util.UUID;

public record ProjectAssistantContext(UUID projectId, String projectName, long currentRevision,
        ProjectDocument document, List<Member> members, List<ProjectAiMessage> recentMessages) {
    public ProjectAssistantContext {
        members = List.copyOf(members);
        recentMessages = List.copyOf(recentMessages);
    }
    public record Member(UUID userId, String displayName, String role) { }
}
