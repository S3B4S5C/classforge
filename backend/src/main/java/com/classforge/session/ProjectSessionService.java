package com.classforge.session;

import com.classforge.activity.*;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.persistence.ProjectRepository;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProjectSessionService {
    private final ProjectSessionRepository sessions;
    private final ProjectSessionParticipantRepository participants;
    private final ProjectSessionSummaryService summaries;
    private final ProjectRepository projects;
    private final ProjectAccessService access;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration inactivity;

    public ProjectSessionService(ProjectSessionRepository sessions, ProjectSessionParticipantRepository participants,
            ProjectSessionSummaryService summaries, ProjectRepository projects, ProjectAccessService access,
            ApplicationEventPublisher events, @Qualifier("projectActivityClock") Clock clock,
            @Value("${classforge.activity.sessions.inactivity-timeout:PT30M}") Duration inactivity) {
        if (inactivity.isZero() || inactivity.isNegative()) throw new IllegalArgumentException("El tiempo de inactividad debe ser positivo.");
        this.sessions = sessions; this.participants = participants; this.summaries = summaries;
        this.projects = projects; this.access = access; this.events = events; this.clock = clock; this.inactivity = inactivity;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectSessionEntity record(ProjectOperationPersistedEvent event, List<ActivityChange> changes) {
        // All writers, explicit closes and maintenance lock the same parent row first.
        lockProject(event.projectId());
        var session = sessions.findByProjectIdAndStatus(event.projectId(), ProjectSessionStatus.OPEN).orElse(null);
        if (session != null && !event.occurredAt().isBefore(session.getLastActivityAt().plus(inactivity))) {
            close(session, session.getLastActivityAt().plus(inactivity), "INACTIVITY", event.occurredAt(), null);
            // The CLOSED document is saved before opening its successor.
            session = null;
        }
        if (session == null) session = sessions.save(new ProjectSessionEntity(event.projectId(), event.revisionBefore(), event.occurredAt()));
        var member = participants.findBySessionIdAndUserId(session.getId(), event.actorUserId())
                .orElse(null);
        if (member == null) member = new ProjectSessionParticipantEntity(session, event.actorUserId(), event.actorDisplayName());
        member.record(event.actorDisplayName(), changes.size());
        participants.save(member);
        // Clock rollback must not move the session's last activity backwards.
        Instant at = event.occurredAt().isBefore(session.getLastActivityAt()) ? session.getLastActivityAt() : event.occurredAt();
        session.record(event.revisionAfter(), at, changes.size(), summaries.increment(session, changes));
        summaries.refresh(session, members(session));
        sessions.save(session);
        return session;
    }

    @Transactional(readOnly = true)
    public ActivityPage<ProjectSession> list(UUID user, UUID project, ProjectSessionStatus status, int page, int size) {
        access.requireRead(user, project); ActivityPage.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("startRevision"), Sort.Order.desc("id")));
        var found = status == null ? sessions.findByProjectId(project, pageable) : sessions.findByProjectIdAndStatus(project, status, pageable);
        return ActivityPage.from(found, this::snapshot);
    }

    @Transactional(readOnly = true)
    public ProjectSession get(UUID user, UUID project, UUID session) {
        access.requireRead(user, project);
        return snapshot(require(project, session));
    }

    @Transactional(readOnly = true)
    public Optional<ProjectSession> getCurrentOrLastSession(UUID user, UUID project) {
        access.requireRead(user, project);
        return sessions.findFirstByProjectIdOrderByStartRevisionDescIdDesc(project).map(this::snapshot);
    }

    @Transactional
    public ProjectSession close(UUID user, UUID project, UUID sessionId) {
        access.requireEdit(user, project); lockProject(project);
        var session = require(project, sessionId);
        if (session.getStatus() == ProjectSessionStatus.OPEN) {
            Instant now = now();
            boolean expired = !now.isBefore(session.getLastActivityAt().plus(inactivity));
            close(session, expired ? session.getLastActivityAt().plus(inactivity) : now,
                    expired ? "INACTIVITY" : "EXPLICIT", now, expired ? null : user);
        }
        return snapshot(session);
    }

    @Transactional(readOnly = true)
    public List<UUID> inactiveProjects() { return sessions.findInactiveProjectIds(now().minus(inactivity)); }

    @Transactional
    public void closeIfInactive(UUID project) {
        lockProject(project);
        sessions.findByProjectIdAndStatus(project, ProjectSessionStatus.OPEN).ifPresent(session -> {
            Instant now = now();
            if (!now.isBefore(session.getLastActivityAt().plus(inactivity))) {
                close(session, session.getLastActivityAt().plus(inactivity), "INACTIVITY", now, null);
            }
        });
    }

    private void close(ProjectSessionEntity session, Instant at, String reason, Instant now, UUID actor) {
        session.close(at.isBefore(session.getLastActivityAt()) ? session.getLastActivityAt() : at, reason, now);
        summaries.refresh(session, members(session));
        sessions.save(session);
        events.publishEvent(new ProjectSessionClosedEvent(snapshot(session), actor));
    }
    private ProjectSessionEntity require(UUID project, UUID session) {
        return sessions.findByIdAndProjectId(session, project).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada."));
    }
    private void lockProject(UUID id) { projects.findForUpdateById(id).orElseThrow(() -> new ProjectNotFoundException(id)); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private List<ProjectSession.Participant> members(ProjectSessionEntity session) {
        return participants.findBySessionIdOrderByUserIdAsc(session.getId()).stream().map(ProjectSessionParticipantEntity::toParticipant).toList();
    }
    private ProjectSession snapshot(ProjectSessionEntity s) {
        return new ProjectSession(s.getId(), s.getProjectId(), s.getStartedAt(), s.getEndedAt(),
                s.getStartRevision(), s.getEndRevision(), s.getSummary(), s.getStatus(), s.getCreatedAt(),
                s.getUpdatedAt(), s.getLastActivityAt(), s.getCloseReason(), s.getTotalOperations(),
                s.getTotalChanges(), summaries.counts(s), members(s));
    }
}
