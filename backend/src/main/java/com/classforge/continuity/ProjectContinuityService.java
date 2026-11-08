package com.classforge.continuity;

import com.classforge.activity.*;
import com.classforge.project.application.*;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.persistence.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ProjectContinuityService {
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService access;
    private final ProjectActivityQueryService activities;
    private final MongoTemplate mongo;
    private final Clock clock;
    public ProjectContinuityService(ProjectService projects, ProjectRepository projectRepository,
            ProjectAccessService access, ProjectActivityQueryService activities, MongoTemplate mongo,
            @Qualifier("projectActivityClock") Clock clock) {
        this.projects = projects; this.projectRepository = projectRepository; this.access = access;
        this.activities = activities; this.mongo = mongo; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjectContinuity sinceLastVisit(UUID user, UUID project, int page, int size) {
        var snapshot = projects.get(user, project);
        var visit = mongo.findById(ProjectUserVisit.key(project, user), ProjectUserVisit.class);
        long revision = visit == null ? 0 : visit.lastSeenRevision();
        var filter = new ProjectActivityFilter(null, null, null, revision, snapshot.revision() + 1, null, null);
        var found = activities.find(user, project, filter, page, size);
        var summary = activities.summarize(user, project, filter);
        return new ProjectContinuity(project, visit == null, visit == null ? null : visit.lastSeenAt(),
                revision, snapshot.revision(), summary.totalChanges(), found.items(), summary.participants(),
                page, size, page + 1 < found.totalPages());
    }

    @Transactional
    public ProjectUserVisit acknowledge(UUID user, UUID project, long revision) {
        access.requireRead(user, project);
        var snapshot = projectRepository.findForUpdateById(project).orElseThrow(() -> new ProjectNotFoundException(project));
        if (revision < 0 || revision > snapshot.getRevision()) throw new IllegalArgumentException("La revisión vista debe pertenecer al estado guardado del proyecto.");
        String id = ProjectUserVisit.key(project, user);
        var existing = mongo.findById(id, ProjectUserVisit.class);
        if (existing != null && revision < existing.lastSeenRevision()) return existing;
        return mongo.save(new ProjectUserVisit(id, project, user, clock.instant().truncatedTo(ChronoUnit.MICROS), revision));
    }
}
