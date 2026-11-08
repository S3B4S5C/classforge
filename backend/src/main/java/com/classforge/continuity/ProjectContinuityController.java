package com.classforge.continuity;

import com.classforge.auth.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectContinuityController {
    private final ProjectContinuityService continuity;
    private final CurrentUser user;
    public ProjectContinuityController(ProjectContinuityService continuity, CurrentUser user) { this.continuity = continuity; this.user = user; }
    @GetMapping("/since-last-visit")
    public ProjectContinuity get(@PathVariable UUID projectId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return continuity.sinceLastVisit(user.id(), projectId, page, size); }
    @PostMapping("/visits")
    public ProjectUserVisit seen(@PathVariable UUID projectId, @Valid @RequestBody Seen request) {
        return continuity.acknowledge(user.id(), projectId, request.revision());
    }
    public record Seen(@jakarta.validation.constraints.NotNull @Min(0) Long revision) { }
}
