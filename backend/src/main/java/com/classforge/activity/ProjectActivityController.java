package com.classforge.activity;

import com.classforge.auth.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/activities")
public class ProjectActivityController {
    private final ProjectActivityQueryService activities;
    private final CurrentUser user;
    public ProjectActivityController(ProjectActivityQueryService activities, CurrentUser user) { this.activities = activities; this.user = user; }

    @GetMapping
    public ActivityPage<ProjectActivity> list(@PathVariable UUID projectId,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) Long afterRevision,
            @RequestParam(required = false) Long beforeRevision, @RequestParam(required = false) ActivityOperationType operationType,
            @RequestParam(required = false) UUID sessionId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activities.find(user.id(), projectId, new ProjectActivityFilter(actorId, from, to,
                afterRevision, beforeRevision, operationType, sessionId), page, size);
    }

    @GetMapping("/{activityId}")
    public ProjectActivity get(@PathVariable UUID projectId, @PathVariable UUID activityId) { return activities.get(user.id(), projectId, activityId); }

    @GetMapping("/summary")
    public ProjectActivitySummary summary(@PathVariable UUID projectId,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) Long afterRevision,
            @RequestParam(required = false) Long beforeRevision, @RequestParam(required = false) ActivityOperationType operationType,
            @RequestParam(required = false) UUID sessionId) {
        return activities.summarize(user.id(), projectId, new ProjectActivityFilter(actorId, from, to, afterRevision, beforeRevision, operationType, sessionId));
    }
}
