package com.classforge.projectassistant;

import com.classforge.assistant.AssistantPlanResponse;
import com.classforge.assistant.AssistantPlanService;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectService;
import com.classforge.projectassistant.persistence.ProjectAssistantMessageStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Conversation orchestration deliberately has no transaction spanning the remote model call. */
@Service
public class ProjectAssistantService {
    private final ProjectAccessService access;
    private final ProjectService projects;
    private final ProjectAssistantMessageStore messages;
    private final ProjectAssistantContextService contexts;
    private final ProjectAssistantConversationGateway gateway;
    private final AssistantPlanService planner;
    private final SimpMessagingTemplate realtime;

    public ProjectAssistantService(ProjectAccessService access, ProjectService projects,
            ProjectAssistantMessageStore messages, ProjectAssistantContextService contexts,
            ProjectAssistantConversationGateway gateway, AssistantPlanService planner, SimpMessagingTemplate realtime) {
        this.access = access; this.projects = projects; this.messages = messages; this.contexts = contexts;
        this.gateway = gateway; this.planner = planner; this.realtime = realtime;
    }

    public ProjectAssistantHistory history(UUID userId, UUID projectId, Long before, int limit) {
        access.requireRead(userId, projectId);
        if (limit < 1 || limit > 100 || (before != null && before < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit debe estar entre 1 y 100; before debe ser positivo.");
        }
        return messages.history(projectId, before, limit);
    }

    public ProjectAssistantResponse send(UUID userId, UUID projectId, ProjectAssistantMessageRequest request) {
        access.requireEdit(userId, projectId);
        var snapshot = projects.get(userId, projectId);
        ProjectAssistantMessageStore.Claim claim;
        try {
            claim = messages.claim(userId, projectId, request, snapshot.revision());
        } catch (DataIntegrityViolationException concurrentInsert) {
            // A concurrent first request can win the unique constraint. Re-read in a fresh transaction.
            claim = messages.claim(userId, projectId, request, snapshot.revision());
        }
        if (claim.existingResponse() != null) {
            return new ProjectAssistantResponse(claim.userMessage(), claim.existingResponse(), claim.existingResponse().projectRevision());
        }
        try {
            if (claim.created()) publish("ASSISTANT_MESSAGE_CREATED", claim.userMessage());
            AssistantPlanResponse plan = null;
            AssistantReview review = null;
            String content;
            long revision = snapshot.revision();
            if (request.mode() == AssistantMode.EDIT) {
                plan = planner.plan(userId, projectId, request.content().trim());
                revision = plan.baseRevision();
                content = "Propuesta sin aplicar: " + plan.summary()
                        + "\nRevisa la previsualización y confirma Aplicar para modificar el diagrama.";
            } else {
                var context = contexts.build(snapshot, request.requestId());
                var response = gateway.respond(context, request.content().trim(), request.mode());
                if (response == null || response.content() == null || response.content().isBlank()
                        || response.content().length() > 20000) {
                    throw new ProjectAssistantUnavailableException("El proveedor no devolvió una respuesta válida.");
                }
                content = response.content();
                if (request.mode() == AssistantMode.REVIEW) {
                    review = new AssistantReview(UUID.randomUUID(), projectId, revision, userId, content,
                            validFindings(response.findings(), context), Instant.now());
                }
            }
            access.requireEdit(userId, projectId);
            var assistant = messages.complete(claim, content, revision, review, plan);
            publish("ASSISTANT_RESPONSE_CREATED", assistant);
            if (review != null) publish("ASSISTANT_REVIEW_COMPLETED", assistant);
            return new ProjectAssistantResponse(claim.userMessage(), assistant, revision);
        } catch (RuntimeException failure) {
            messages.fail(claim);
            throw failure;
        }
    }

    private List<AssistantReviewFinding> validFindings(List<AssistantReviewFinding> findings, ProjectAssistantContext context) {
        Set<String> references = new HashSet<>();
        context.document().umlModel().classes().forEach(element -> {
            references.add(element.id().toString());
            element.attributes().forEach(attribute -> references.add(attribute.id().toString()));
        });
        context.document().umlModel().relationships().forEach(element -> references.add(element.id().toString()));
        return findings.stream().filter(Objects::nonNull)
                .filter(finding -> finding.severity() != null && Set.of("INFO", "WARNING", "ERROR").contains(finding.severity()))
                .filter(finding -> finding.message() != null && !finding.message().isBlank() && finding.message().length() <= 2000)
                .filter(finding -> finding.category() != null && !finding.category().isBlank() && finding.category().length() <= 80)
                .filter(finding -> finding.elementRef() == null || references.contains(finding.elementRef()))
                .limit(100).toList();
    }

    private void publish(String type, ProjectAiMessage message) {
        realtime.convertAndSend("/topic/projects/" + message.projectId() + "/assistant",
                new Event(type, message.projectId(), message));
    }

    public record Event(String type, UUID projectId, ProjectAiMessage message) { }
}
