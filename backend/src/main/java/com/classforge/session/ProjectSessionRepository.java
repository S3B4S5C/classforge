package com.classforge.session;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.*;
public interface ProjectSessionRepository extends MongoRepository<ProjectSessionEntity, UUID> {
    Optional<ProjectSessionEntity> findByProjectIdAndStatus(UUID projectId, ProjectSessionStatus status);
    Optional<ProjectSessionEntity> findFirstByProjectIdOrderByStartRevisionDescIdDesc(UUID projectId);
    Optional<ProjectSessionEntity> findByIdAndProjectId(UUID id, UUID projectId);
    Page<ProjectSessionEntity> findByProjectId(UUID projectId, Pageable pageable);
    Page<ProjectSessionEntity> findByProjectIdAndStatus(UUID projectId, ProjectSessionStatus status, Pageable pageable);
    List<ProjectSessionEntity> findByStatusAndLastActivityAtLessThanEqual(ProjectSessionStatus status, Instant cutoff);
    default List<UUID> findInactiveProjectIds(Instant cutoff) {
        return findByStatusAndLastActivityAtLessThanEqual(ProjectSessionStatus.OPEN, cutoff).stream().map(ProjectSessionEntity::getProjectId).toList();
    }
}
