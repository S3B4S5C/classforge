package com.classforge.activity;

import com.classforge.auth.persistence.UserRepository;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.Project;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class ProjectChangePublisher {
    private final ApplicationEventPublisher events;
    private final UserRepository users;
    private final Clock clock;

    public ProjectChangePublisher(ApplicationEventPublisher events, UserRepository users,
            @Qualifier("projectActivityClock") Clock clock) {
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID actor, UUID operationId, ActivitySource source,
            UmlCommandPayload command, Project before, Project after) {
        // Existing internal fixtures can use an owner UUID without an app_users row.
        String name = users.findById(actor).map(user -> user.getDisplayName()).orElse("Usuario " + actor);
        events.publishEvent(new ProjectOperationPersistedEvent(after.id(),
                operationId == null ? UUID.randomUUID() : operationId, actor, name,
                before.revision(), after.revision(), ActivitySource.orManual(source), command,
                before.document(), after.document(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
