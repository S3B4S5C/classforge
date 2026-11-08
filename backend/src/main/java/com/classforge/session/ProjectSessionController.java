package com.classforge.session;

import com.classforge.activity.ActivityPage;
import com.classforge.auth.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/sessions")
public class ProjectSessionController {
    private final ProjectSessionService sessions;
    private final CurrentUser user;
    public ProjectSessionController(ProjectSessionService sessions, CurrentUser user) { this.sessions = sessions; this.user = user; }
    @GetMapping
    public ActivityPage<ProjectSession> list(@PathVariable UUID projectId, @RequestParam(required = false) ProjectSessionStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return sessions.list(user.id(), projectId, status, page, size);
    }
    @GetMapping("/current-or-last")
    public ResponseEntity<ProjectSession> currentOrLast(@PathVariable UUID projectId) {
        return sessions.getCurrentOrLastSession(user.id(), projectId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @GetMapping("/{sessionId}")
    public ProjectSession get(@PathVariable UUID projectId, @PathVariable UUID sessionId) { return sessions.get(user.id(), projectId, sessionId); }
    @PostMapping("/{sessionId}/close")
    public ProjectSession close(@PathVariable UUID projectId, @PathVariable UUID sessionId) { return sessions.close(user.id(), projectId, sessionId); }
}
