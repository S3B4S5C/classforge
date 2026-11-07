package com.classforge.projectassistant;

import com.classforge.auth.persistence.UserRepository;
import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.projectassistant.persistence.ProjectAssistantMessageStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProjectAssistantContextService {
    private final ProjectMembershipRepository memberships;
    private final UserRepository users;
    private final ProjectAssistantMessageStore messages;

    public ProjectAssistantContextService(ProjectMembershipRepository memberships, UserRepository users, ProjectAssistantMessageStore messages) {
        this.memberships = memberships; this.users = users; this.messages = messages;
    }

    @Transactional(readOnly = true)
    public ProjectAssistantContext build(Project snapshot, UUID excludedRequest) {
        Map<UUID, String> roles = new LinkedHashMap<>();
        roles.put(snapshot.ownerId(), "OWNER");
        memberships.findAllByProjectIdOrderByCreatedAtAsc(snapshot.id()).stream()
                .filter(member -> member.getRole() == ProjectAccessRole.EDITOR)
                .forEach(member -> roles.putIfAbsent(member.getUserId(), "EDITOR"));
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(roles.keySet()).forEach(user -> names.put(user.getId(), user.getDisplayName()));
        var members = roles.entrySet().stream().map(entry -> new ProjectAssistantContext.Member(
                entry.getKey(), names.getOrDefault(entry.getKey(), "Integrante"), entry.getValue())).toList();
        return new ProjectAssistantContext(snapshot.id(), snapshot.name(), snapshot.revision(), snapshot.document(),
                members, messages.recent(snapshot.id(), excludedRequest));
    }
}
