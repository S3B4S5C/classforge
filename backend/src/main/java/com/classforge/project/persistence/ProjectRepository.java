package com.classforge.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    List<ProjectEntity> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    Optional<ProjectEntity> findByIdAndOwnerId(UUID projectId, UUID ownerId);
}