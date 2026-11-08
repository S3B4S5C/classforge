package com.classforge.notification;

import com.classforge.activity.*;
import com.classforge.project.persistence.*;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.session.ProjectSessionClosedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProjectNotificationService {
    private final MongoTemplate mongo;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public ProjectNotificationService(MongoTemplate mongo, ProjectRepository projects, ProjectMembershipRepository memberships,
            ApplicationEventPublisher events, @Qualifier("projectActivityClock") Clock clock) {
        this.mongo = mongo; this.projects = projects; this.memberships = memberships; this.events = events; this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void activity(ProjectActivityRecordedEvent event) {
        var a = event.activity();
        for (UUID recipient : recipients(a.projectId(), a.actorUserId())) {
            create(a.projectId(), recipient, ProjectNotification.Type.PROJECT_ACTIVITY, "Actividad del proyecto",
                    a.actorDisplayName() + " " + a.description(), a.id(), a.sessionId(), "activity:" + a.id(), a.createdAt());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void sessionClosed(ProjectSessionClosedEvent event) {
        var s = event.session();
        for (UUID recipient : recipients(s.projectId(), event.actorUserId())) {
            create(s.projectId(), recipient, ProjectNotification.Type.SESSION_CLOSED, "Sesión de trabajo cerrada",
                    "Se cerró una sesión del proyecto. Revisión " + s.startRevision() + " → " + s.endRevision()
                            + "; " + s.totalOperations() + " operaciones.", null, s.id(), "session:" + s.id(), s.updatedAt());
        }
    }

    private Set<UUID> recipients(UUID project, UUID excluded) {
        var p = projects.findById(project).orElseThrow(() -> new ProjectNotFoundException(project));
        Set<UUID> ids = new LinkedHashSet<>(); ids.add(p.getOwnerId());
        memberships.findAllByProjectIdOrderByCreatedAtAsc(project).stream()
                .filter(m -> m.getRole().canRead()).forEach(m -> ids.add(m.getUserId()));
        ids.remove(excluded);
        return ids;
    }
    private void create(UUID project, UUID recipient, ProjectNotification.Type type, String title, String message,
            UUID activity, UUID session, String event, Instant at) {
        String key = event + ":" + recipient;
        UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        if (mongo.exists(Query.query(Criteria.where("id").is(id)), ProjectNotification.class)) return;
        var notification = mongo.insert(new ProjectNotification(id, project, recipient, type, title, message,
                activity, session, key, false, at, null));
        events.publishEvent(new ProjectNotificationEvent(recipient, "NOTIFICATION_CREATED", notification));
    }

    @Transactional(readOnly = true)
    public ActivityPage<ProjectNotification> list(UUID user, UUID project, boolean unreadOnly, int page, int size) {
        ActivityPage.validate(page, size);
        var criteria = Criteria.where("recipientUserId").is(user);
        if (project != null) criteria.and("projectId").is(project);
        if (unreadOnly) criteria.and("read").is(false);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        long total = mongo.count(Query.query(criteria), ProjectNotification.class);
        var result = mongo.find(Query.query(criteria).with(pageable), ProjectNotification.class);
        return ActivityPage.from(new PageImpl<>(result, pageable, total), n -> n);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID user) {
        return mongo.count(Query.query(Criteria.where("recipientUserId").is(user).and("read").is(false)), ProjectNotification.class);
    }

    @Transactional
    public ProjectNotification markRead(UUID user, UUID id) {
        var query = Query.query(Criteria.where("id").is(id).and("recipientUserId").is(user));
        var notification = mongo.findOne(query, ProjectNotification.class);
        if (notification == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada.");
        if (!notification.read()) {
            mongo.updateFirst(query, new Update().set("read", true).set("readAt", now()), ProjectNotification.class);
            events.publishEvent(new ProjectNotificationEvent(user, "NOTIFICATIONS_CHANGED", null));
        }
        return mongo.findOne(query, ProjectNotification.class);
    }

    @Transactional
    public long markAllRead(UUID user, UUID project) {
        var criteria = Criteria.where("recipientUserId").is(user).and("read").is(false);
        if (project != null) criteria.and("projectId").is(project);
        long count = mongo.updateMulti(Query.query(criteria), new Update().set("read", true).set("readAt", now()), ProjectNotification.class).getModifiedCount();
        if (count > 0) events.publishEvent(new ProjectNotificationEvent(user, "NOTIFICATIONS_CHANGED", null));
        return count;
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
