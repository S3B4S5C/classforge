package com.classforge.session;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;

public interface ProjectSessionParticipantRepository extends MongoRepository<ProjectSessionParticipantEntity, UUID> {
    Optional<ProjectSessionParticipantEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);
    List<ProjectSessionParticipantEntity> findBySessionIdOrderByUserIdAsc(UUID sessionId);
}
