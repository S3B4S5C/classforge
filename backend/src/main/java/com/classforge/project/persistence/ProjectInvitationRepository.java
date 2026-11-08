package com.classforge.project.persistence;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;
public interface ProjectInvitationRepository extends MongoRepository<ProjectInvitationEntity, UUID> {
    boolean existsByProjectIdAndInvitedEmailIgnoreCaseAndStatus(UUID projectId, String email, ProjectInvitationStatus status);
    List<ProjectInvitationEntity> findAllByInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, ProjectInvitationStatus status);
    List<ProjectInvitationEntity> findAllByProjectIdAndStatusOrderByCreatedAtAsc(UUID projectId, ProjectInvitationStatus status);
    // Mutations acquire the owning project's transactional write fence first.
    default Optional<UUID> findProjectIdById(UUID id) { return findById(id).map(ProjectInvitationEntity::getProjectId); }
    default Optional<ProjectInvitationEntity> findForUpdateById(UUID id) { return findById(id); }
}
