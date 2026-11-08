package com.classforge.activity;
import com.classforge.project.access.ProjectAccessService;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProjectActivityQueryService {
    private final ProjectAccessService access;
    private final MongoTemplate mongo;
    public ProjectActivityQueryService(ProjectAccessService access, MongoTemplate mongo) { this.access = access; this.mongo = mongo; }
    public ActivityPage<ProjectActivity> find(UUID user, UUID project, ProjectActivityFilter filter, int page, int size) {
        access.requireRead(user, project); ActivityPage.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "revisionAfter"));
        long count = mongo.count(Query.query(filter.inProject(project)), ProjectActivityEntity.class);
        var items = mongo.find(Query.query(filter.inProject(project)).with(pageable), ProjectActivityEntity.class);
        return ActivityPage.from(new PageImpl<>(items, pageable, count), ProjectActivityEntity::toActivity);
    }
    public ProjectActivity get(UUID user, UUID project, UUID activity) {
        access.requireRead(user, project);
        var found = mongo.findOne(Query.query(Criteria.where("projectId").is(project).and("id").is(activity)), ProjectActivityEntity.class);
        if (found == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada.");
        return found.toActivity();
    }
    public ProjectActivitySummary summarize(UUID user, UUID project, ProjectActivityFilter filter) {
        access.requireRead(user, project);
        var aggregation = Aggregation.newAggregation(Aggregation.match(filter.inProject(project)),
                Aggregation.sort(Sort.Direction.ASC, "revisionAfter"), Aggregation.group("actorUserId")
                        .last("actorDisplayName").as("displayName").count().as("changes")
                        .min("revisionBefore").as("firstRevision").max("revisionAfter").as("lastRevision"));
        var rows = mongo.aggregate(aggregation, ProjectActivityEntity.class, ActorAggregate.class).getMappedResults();
        var participants = rows.stream().sorted(Comparator.comparing(ActorAggregate::id))
                .map(row -> new ProjectActivitySummary.Participant(row.id(), row.displayName(), row.changes())).toList();
        Long from = rows.stream().map(ActorAggregate::firstRevision).min(Long::compare).orElse(null);
        Long to = rows.stream().map(ActorAggregate::lastRevision).max(Long::compare).orElse(null);
        return new ProjectActivitySummary(project, from, to, rows.stream().mapToLong(ActorAggregate::changes).sum(), participants);
    }
    public record ActorAggregate(UUID id, String displayName, long changes, Long firstRevision, Long lastRevision) { }

    public ActivityPage<ProjectActivity> getRecentActivity(UUID user, UUID project) { return find(user, project, ProjectActivityFilter.all(), 0, 20); }
    public ActivityPage<ProjectActivity> getActivityByActor(UUID user, UUID project, UUID actor) {
        return find(user, project, new ProjectActivityFilter(actor, null, null, null, null, null, null), 0, 20);
    }
    public ActivityPage<ProjectActivity> getSinceRevision(UUID user, UUID project, long revision) {
        return find(user, project, new ProjectActivityFilter(null, null, null, revision, null, null, null), 0, 20);
    }
    public ActivityPage<ProjectActivity> findSince(UUID user, UUID project, Instant time) { return findBetween(user, project, time, null); }
    public ActivityPage<ProjectActivity> findBetween(UUID user, UUID project, Instant from, Instant to) {
        return find(user, project, new ProjectActivityFilter(null, from, to, null, null, null, null), 0, 20);
    }
}
