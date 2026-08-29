package com.classforge.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMembershipRepository
        extends JpaRepository<ProjectMembershipEntity, UUID> {

    Optional<ProjectMembershipEntity> findByProjectIdAndUserId(
            UUID projectId,
            UUID userId
    );

    List<ProjectMembershipEntity> findAllByUserId(UUID userId);

    List<ProjectMembershipEntity> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    boolean existsByProjectIdAndUserId(
            UUID projectId,
            UUID userId
    );
}
