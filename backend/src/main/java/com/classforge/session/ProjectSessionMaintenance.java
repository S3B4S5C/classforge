package com.classforge.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "classforge.activity.sessions.maintenance-enabled", matchIfMissing = true)
public class ProjectSessionMaintenance {
    private static final Logger log = LoggerFactory.getLogger(ProjectSessionMaintenance.class);
    private final ProjectSessionService sessions;
    public ProjectSessionMaintenance(ProjectSessionService sessions) { this.sessions = sessions; }

    @Scheduled(fixedDelayString = "${classforge.activity.sessions.sweep-interval:PT1M}", initialDelayString = "${classforge.activity.sessions.sweep-interval:PT1M}")
    public void closeInactiveSessions() {
        for (var project : sessions.inactiveProjects()) {
            try { sessions.closeIfInactive(project); }
            catch (RuntimeException failure) { log.error("No se pudo cerrar la sesión inactiva del proyecto {}", project, failure); }
        }
    }
}
