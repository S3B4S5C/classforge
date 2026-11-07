package com.classforge.projectassistant.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProjectAiMessageRepository extends JpaRepository<ProjectAiMessageEntity, Long> {
    Optional<ProjectAiMessageEntity> findByProjectIdAndRequestIdAndRole(UUID projectId, UUID requestId, String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectAiMessageEntity m where m.projectId=:project and m.requestId=:request and m.role='USER'")
    Optional<ProjectAiMessageEntity> lockRequest(@Param("project") UUID project, @Param("request") UUID request);

    List<ProjectAiMessageEntity> findByProjectIdAndSequenceLessThanOrderBySequenceDesc(UUID projectId, long before, Pageable pageable);

    @Query("select m from ProjectAiMessageEntity m where m.projectId=:project and m.requestId<>:excluded order by m.sequence desc")
    List<ProjectAiMessageEntity> recent(@Param("project") UUID project, @Param("excluded") UUID excluded, Pageable pageable);
}
