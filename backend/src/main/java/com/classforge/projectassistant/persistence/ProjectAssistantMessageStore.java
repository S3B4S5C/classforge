package com.classforge.projectassistant.persistence;

import com.classforge.assistant.AssistantPlanResponse;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.projectassistant.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

/** Short transactions only: model requests are made by the facade after these methods return. */
@Service
public class ProjectAssistantMessageStore {
    private final ProjectAiMessageRepository messages;
    private final UserRepository users;
    private final JsonMapper json;

    public ProjectAssistantMessageStore(ProjectAiMessageRepository messages, UserRepository users, JsonMapper json) {
        this.messages = messages; this.users = users; this.json = json;
    }

    @Transactional
    public Claim claim(UUID userId, UUID projectId, ProjectAssistantMessageRequest request, long revision) {
        var existing = messages.lockRequest(projectId, request.requestId());
        if (existing.isEmpty()) {
            var author = users.findById(userId).orElseThrow();
            var created = messages.save(ProjectAiMessageEntity.user(projectId, request.requestId(), userId,
                    author.getDisplayName(), request.mode(), request.content().trim(), revision));
            return new Claim(created.toMessage(json), created.getGenerationToken(), null, true);
        }
        var user = existing.get();
        if (!userId.equals(user.getAuthorUserId()) || user.getType() != request.mode()
                || !user.getContent().equals(request.content().trim())) {
            throw new ProjectAssistantConflictException("Ese identificador ya pertenece a otra solicitud.");
        }
        var response = messages.findByProjectIdAndRequestIdAndRole(projectId, request.requestId(), "ASSISTANT");
        if (response.isPresent()) return new Claim(user.toMessage(json), null, response.get().toMessage(json), false);
        // Recover an abandoned request after ten minutes; a token fences a late old response.
        if ("PROCESSING".equals(user.getGenerationStatus())
                && user.getGenerationUpdatedAt().isAfter(Instant.now().minus(Duration.ofMinutes(10)))) {
            throw new ProjectAssistantConflictException("La solicitud sigue en proceso. Reintenta en unos segundos.");
        }
        user.startGeneration();
        messages.save(user);
        return new Claim(user.toMessage(json), user.getGenerationToken(), null, false);
    }

    @Transactional
    public ProjectAiMessage complete(Claim claim, String content, long revision, AssistantReview review, AssistantPlanResponse plan) {
        var user = messages.lockRequest(claim.userMessage().projectId(), claim.userMessage().requestId()).orElseThrow();
        if (!Objects.equals(claim.token(), user.getGenerationToken()) || !"PROCESSING".equals(user.getGenerationStatus())) {
            throw new ProjectAssistantConflictException("La solicitud fue recuperada por otro intento. Actualiza el historial.");
        }
        var response = messages.save(ProjectAiMessageEntity.assistant(claim.userMessage().projectId(),
                claim.userMessage().requestId(), claim.userMessage().type(), content, revision, review, plan, json));
        user.finishGeneration("COMPLETED");
        messages.save(user);
        return response.toMessage(json);
    }

    @Transactional
    public void fail(Claim claim) {
        messages.lockRequest(claim.userMessage().projectId(), claim.userMessage().requestId()).ifPresent(user -> {
            if (Objects.equals(user.getGenerationToken(), claim.token()) && "PROCESSING".equals(user.getGenerationStatus())) {
                user.finishGeneration("FAILED");
                messages.save(user);
            }
        });
    }

    @Transactional(readOnly = true)
    public ProjectAssistantHistory history(UUID projectId, Long before, int limit) {
        var page = new ArrayList<>(messages.findByProjectIdAndSequenceLessThanOrderBySequenceDesc(
                projectId, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, limit + 1)));
        boolean hasMore = page.size() > limit;
        if (hasMore) page.removeLast();
        Collections.reverse(page);
        return new ProjectAssistantHistory(page.stream().map(message -> message.toMessage(json)).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public List<ProjectAiMessage> recent(UUID projectId, UUID excludedRequest) {
        var recent = new ArrayList<>(messages.recent(projectId, excludedRequest, PageRequest.of(0, 20)));
        Collections.reverse(recent);
        return recent.stream().map(message -> message.toMessage(json)).toList();
    }

    public record Claim(ProjectAiMessage userMessage, UUID token, ProjectAiMessage existingResponse, boolean created) { }
}
