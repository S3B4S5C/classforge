package com.classforge.project.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectInvitationRepository
        extends JpaRepository<ProjectInvitationEntity, UUID> {

    boolean existsByProjectIdAndInvitedEmailIgnoreCaseAndStatus(
            UUID projectId,
            String invitedEmail,
            ProjectInvitationStatus status
    );

    List<ProjectInvitationEntity> findAllByInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            String invitedEmail,
            ProjectInvitationStatus status
    );

    List<ProjectInvitationEntity> findAllByProjectIdAndStatusOrderByCreatedAtAsc(
            UUID projectId,
            ProjectInvitationStatus status
    );

    @Query("""
            select invitation.projectId
            from ProjectInvitationEntity invitation
            where invitation.id = :invitationId
            """)
    Optional<UUID> findProjectIdById(
            @Param("invitationId") UUID invitationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from ProjectInvitationEntity invitation
            where invitation.id = :invitationId
            """)
    Optional<ProjectInvitationEntity> findForUpdateById(
            @Param("invitationId") UUID invitationId
    );
}
