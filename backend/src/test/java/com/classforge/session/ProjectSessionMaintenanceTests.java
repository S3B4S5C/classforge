package com.classforge.session;

import com.classforge.activity.ActivitySource;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.document.ProjectDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.annotation.DirtiesContext;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.mongodb.database=session_maintenance",
        "classforge.activity.sessions.maintenance-enabled=true",
        "classforge.activity.sessions.inactivity-timeout=PT30M",
        "classforge.activity.sessions.sweep-interval=PT0.1S"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectSessionMaintenanceTests {
    @Autowired ProjectService projects;
    @Autowired ProjectSessionService sessions;
    static final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-08T12:00:00Z"));
    @TestBean(name = "projectActivityClock", methodName = "testClock") Clock clock;

    static Clock testClock() {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time.get(), zone); }
            @Override public Instant instant() { return time.get(); }
        };
    }

    @Test void scheduledSweepClosesAnInactiveSessionWithoutAnySubsequentRequest() throws Exception {
        Instant now = Instant.parse("2026-11-08T12:00:00Z"); time.set(now);
        UUID owner = UUID.randomUUID(); var project = projects.create(owner, "Cierre automático");
        projects.saveDocument(owner, project.id(), 0, ProjectDocument.empty(), ActivitySource.SYSTEM, null);
        assertEquals(ProjectSessionStatus.OPEN, sessions.getCurrentOrLastSession(owner, project.id()).orElseThrow().status());
        time.set(now.plusSeconds(1800));
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        ProjectSession result;
        do {
            result = sessions.getCurrentOrLastSession(owner, project.id()).orElseThrow();
            if (result.status() == ProjectSessionStatus.CLOSED) break;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        assertEquals(ProjectSessionStatus.CLOSED, result.status());
        assertEquals("INACTIVITY", result.closeReason()); assertEquals(now.plusSeconds(1800), result.endedAt());
    }

    @Test void maintenanceContinuesOtherProjectsWhenOneCloseFails() {
        var service = mock(ProjectSessionService.class);
        UUID failed = UUID.randomUUID(), healthy = UUID.randomUUID();
        when(service.inactiveProjects()).thenReturn(List.of(failed, healthy));
        doThrow(new IllegalStateException("Database temporarily unavailable")).when(service).closeIfInactive(failed);
        new ProjectSessionMaintenance(service).closeInactiveSessions();
        verify(service).closeIfInactive(healthy);
    }
}
