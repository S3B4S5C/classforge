package com.classforge.activity;

import java.time.Instant;
import java.util.*;

public record ProjectActivityFilter(UUID actorId, Instant from, Instant to, Long afterRevision,
        Long beforeRevision, ActivityOperationType operationType, UUID sessionId) {
    public static ProjectActivityFilter all() { return new ProjectActivityFilter(null, null, null, null, null, null, null); }
    public ProjectActivityFilter {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from no puede ser posterior a to.");
        if ((afterRevision != null && afterRevision < 0) || (beforeRevision != null && beforeRevision < 0)) throw new IllegalArgumentException("Las revisiones deben ser >= 0.");
        if (afterRevision != null && beforeRevision != null && afterRevision >= beforeRevision) throw new IllegalArgumentException("afterRevision debe ser menor que beforeRevision.");
    }
    public org.springframework.data.mongodb.core.query.Criteria inProject(UUID project) {
        var criteria = org.springframework.data.mongodb.core.query.Criteria.where("projectId").is(project);
        if (actorId != null) criteria.and("actorUserId").is(actorId);
        if (from != null || to != null) {
            var time = criteria.and("createdAt");
            if (from != null) time.gte(from);
            if (to != null) time.lte(to);
        }
        if (afterRevision != null || beforeRevision != null) {
            var revision = criteria.and("revisionAfter");
            if (afterRevision != null) revision.gt(afterRevision);
            if (beforeRevision != null) revision.lt(beforeRevision);
        }
        if (operationType != null) criteria.and("operationType").is(operationType);
        if (sessionId != null) criteria.and("sessionId").is(sessionId);
        return criteria;
    }
}
