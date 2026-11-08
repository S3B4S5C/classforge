package com.classforge.projectassistant.persistence;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;
public interface ProjectAiMessageRepository extends MongoRepository<ProjectAiMessageEntity, UUID>, ProjectAiRequestWriteAccess {
    Optional<ProjectAiMessageEntity> findByProjectIdAndRequestIdAndRole(UUID project, UUID request, String role);
    List<ProjectAiMessageEntity> findByProjectIdAndSequenceLessThanOrderBySequenceDesc(UUID project, long before, Pageable pageable);
    List<ProjectAiMessageEntity> findByProjectIdAndRequestIdNotOrderBySequenceDesc(UUID project, UUID excluded, Pageable pageable);
    default List<ProjectAiMessageEntity> recent(UUID project, UUID excluded, Pageable pageable) {
        return findByProjectIdAndRequestIdNotOrderBySequenceDesc(project, excluded, pageable);
    }
}
