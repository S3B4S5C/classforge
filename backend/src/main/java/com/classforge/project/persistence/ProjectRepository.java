package com.classforge.project.persistence;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;
public interface ProjectRepository extends MongoRepository<ProjectEntity, UUID>, ProjectWriteAccess {
    List<ProjectEntity> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    Optional<ProjectEntity> findByIdAndOwnerId(UUID projectId, UUID ownerId);
    boolean existsByIdAndOwnerId(UUID projectId, UUID ownerId);
    default Optional<ProjectEntity> findForUpdateById(UUID id) { return acquireWrite(id); }
    default Optional<ProjectEntity> findForUpdate(UUID id, UUID owner) { return acquireWrite(id).filter(p -> p.getOwnerId().equals(owner)); }
}
