package com.classforge.activity;

import com.classforge.ClassForgeApplication;
import com.classforge.auth.persistence.*;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.document.*;
import com.classforge.session.*;
import com.classforge.continuity.*;
import com.classforge.notification.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectActivityRestartTests {
    final String database = "cf_restart_" + UUID.randomUUID().toString().replace("-", "");
    ConfigurableApplicationContext open() {
        // Both application starts use the same isolated MongoDB database.
        return new SpringApplicationBuilder(ClassForgeApplication.class).run(
                "--server.port=0", "--spring.mongodb.uri=" + System.getenv().getOrDefault("CLASSFORGE_TEST_MONGODB_URI", "mongodb://localhost:27017/?replicaSet=rs0"),
                "--spring.mongodb.database=" + database, "--classforge.activity.sessions.maintenance-enabled=false",
                "--spring.jmx.enabled=false", "--logging.level.root=ERROR");
    }

    @Test void historySessionsParticipantsAndSummariesSurviveACompleteApplicationRestart() {
        UUID userId = UUID.randomUUID(), editorId = UUID.randomUUID(); UUID projectId, activityId, sessionId;
        ProjectNotification notification;
        ProjectActivity activity; ProjectSession session;
        try (var app = open()) {
            app.getBean(UserRepository.class).save(new UserEntity(userId, "Autor persistente", userId + "@test.invalid", "unused", Instant.now()));
            app.getBean(UserRepository.class).save(new UserEntity(editorId, "Editor persistente", editorId + "@test.invalid", "unused", Instant.now()));
            var projects = app.getBean(ProjectService.class);
            var project = projects.create(userId, "Persistencia real"); projectId = project.id();
            var doc = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(UUID.randomUUID(), "Cliente", List.of())), List.of()), DiagramLayout.empty());
            app.getBean(com.classforge.project.persistence.ProjectMembershipRepository.class).save(
                    com.classforge.project.persistence.ProjectMembershipEntity.editor(projectId, editorId));
            projects.saveDocument(editorId, projectId, 0, doc);
            app.getBean(ProjectContinuityService.class).acknowledge(userId, projectId, 1);
            notification = app.getBean(ProjectNotificationService.class).list(userId, null, false, 0, 20).items().getFirst();
            activity = app.getBean(ProjectActivityQueryService.class).getRecentActivity(userId, projectId).items().getFirst();
            activityId = activity.id(); sessionId = activity.sessionId();
            session = app.getBean(ProjectSessionService.class).close(userId, projectId, sessionId);
        }
        try (var app = open()) {
            assertEquals(activity, app.getBean(ProjectActivityQueryService.class).get(userId, projectId, activityId));
            assertEquals(session, app.getBean(ProjectSessionService.class).get(userId, projectId, sessionId));
            var continuity = app.getBean(ProjectContinuityService.class).sinceLastVisit(userId, projectId, 0, 20);
            assertEquals(1, continuity.lastSeenRevision()); assertEquals(0, continuity.newActivityCount());
            assertEquals(notification, app.getBean(ProjectNotificationService.class).list(userId, null, false, 0, 20).items().getFirst());
            app.getBean(ProjectNotificationService.class).markRead(userId, notification.id());
            var projects = app.getBean(ProjectService.class);
            var current = projects.get(userId, projectId);
            projects.saveDocument(userId, projectId, current.revision(), current.document());
            var next = app.getBean(ProjectSessionService.class).getCurrentOrLastSession(userId, projectId).orElseThrow();
            assertNotEquals(sessionId, next.id()); assertEquals(1, next.startRevision()); assertEquals(2, next.endRevision());
            assertEquals(2, app.getBean(ProjectActivityQueryService.class).getRecentActivity(userId, projectId).totalElements());
        }
        try (var app = open()) {
            assertTrue(app.getBean(org.springframework.data.mongodb.core.MongoTemplate.class).findById(notification.id(), ProjectNotification.class).read());
            assertEquals(0, app.getBean(ProjectNotificationService.class).unreadCount(userId));
            app.getBean(org.springframework.data.mongodb.core.MongoTemplate.class).getDb().drop();
        }
    }
}
