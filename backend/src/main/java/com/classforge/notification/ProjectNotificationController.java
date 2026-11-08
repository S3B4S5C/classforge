package com.classforge.notification;

import com.classforge.activity.ActivityPage;
import com.classforge.auth.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class ProjectNotificationController {
    private final ProjectNotificationService notifications;
    private final CurrentUser user;
    public ProjectNotificationController(ProjectNotificationService notifications, CurrentUser user) { this.notifications = notifications; this.user = user; }
    @GetMapping
    public ActivityPage<ProjectNotification> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return notifications.list(user.id(), projectId, unreadOnly, page, size); }
    @GetMapping("/unread-count")
    public Map<String,Long> count() { return Map.of("count", notifications.unreadCount(user.id())); }
    @PostMapping("/{id}/read")
    public ProjectNotification read(@PathVariable UUID id) { return notifications.markRead(user.id(), id); }
    @PostMapping("/read-all")
    public Map<String,Long> all(@RequestParam(required = false) UUID projectId) { return Map.of("updated", notifications.markAllRead(user.id(), projectId)); }
}
