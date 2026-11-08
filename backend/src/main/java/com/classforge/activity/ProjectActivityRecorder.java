package com.classforge.activity;

import com.classforge.session.ProjectSessionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProjectActivityRecorder {
    private final ProjectActivityRepository activities;
    private final ProjectActivityDescriptionFactory descriptions;
    private final ProjectSessionService sessions;
    private final JsonMapper json;
    private final org.springframework.context.ApplicationEventPublisher events;

    public ProjectActivityRecorder(ProjectActivityRepository activities, ProjectActivityDescriptionFactory descriptions,
            ProjectSessionService sessions, JsonMapper json, org.springframework.context.ApplicationEventPublisher events) {
        this.activities = activities; this.descriptions = descriptions; this.sessions = sessions; this.json = json;
        this.events = events;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ProjectOperationPersistedEvent event) {
        var changes = descriptions.changes(event);
        var session = sessions.record(event, changes);
        var type = event.command() == null ? ActivityOperationType.SAVE_DOCUMENT
                : ActivityOperationType.valueOf(event.command().type().name());
        var payload = new ProjectActivityPayload(1, event.command(), event.beforeDocument(), event.afterDocument(), changes);
        var activity = activities.save(new ProjectActivityEntity(event, session, type,
                descriptions.description(type, changes), json.writeValueAsString(payload)));
        events.publishEvent(new ProjectActivityRecordedEvent(activity.toActivity()));
    }
}
