package com.classforge.project.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    List<ProjectEntity> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<ProjectEntity> findByIdAndOwnerId(UUID projectId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select project
            from ProjectEntity project
            where project.id = :projectId
              and project.ownerId = :ownerId
            """)
    Optional<ProjectEntity> findForUpdate(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId
    );
}