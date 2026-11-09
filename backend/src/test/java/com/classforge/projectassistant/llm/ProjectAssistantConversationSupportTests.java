package com.classforge.projectassistant.llm;

import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.projectassistant.AssistantMode;
import com.classforge.projectassistant.ProjectAiMessage;
import com.classforge.projectassistant.ProjectAssistantContext;
import com.classforge.projectassistant.ProjectAssistantUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectAssistantConversationSupportTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID projectId = UUID.randomUUID(), userId = UUID.randomUUID();

    @Test
    void growingConversationRetainsNewestCompleteAuthoredMessagesWithinBudgetAndPreservesMandatorySnapshot() {
        String firstQuestion = padded("pregunta-anterior-1", 4000);
        String firstAnswer = padded("respuesta-anterior-1", 5000);
        String secondQuestion = padded("pregunta-anterior-2", 4000);
        String secondAnswer = padded("respuesta-anterior-2", 5000);
        String activeQuestion = padded("pregunta-actual-3", 4000);
        ProjectDocument document = document("Cliente");
        var context = context(document, List.of(
                message(1, "USER", "Ana", firstQuestion), message(2, "ASSISTANT", "ClassForge", firstAnswer),
                message(3, "USER", "Ana", secondQuestion), message(4, "ASSISTANT", "ClassForge", secondAnswer)));

        var turns = assertDoesNotThrow(() -> new ProjectAssistantConversationSupport(mapper, 18000)
                .conversation(context, activeQuestion, AssistantMode.ASK), "A growing history must not block the next question");

        assertEquals(5, turns.size(), "Keep the newest complete question/answer, plus system, snapshot and current question");
        assertEquals("system", turns.get(0).role());
        var snapshot = mapper.readTree(turns.get(1).content());
        assertEquals(mapper.valueToTree(document), snapshot.get("document"));
        assertEquals(7L, snapshot.get("currentRevision").asLong());
        assertEquals("user", turns.get(2).role());
        var recentQuestion = mapper.readTree(turns.get(2).content());
        assertEquals("Ana", recentQuestion.get("authorDisplayName").asString());
        assertEquals(secondQuestion, recentQuestion.get("content").asString());
        assertEquals("assistant", turns.get(3).role());
        var recentAnswer = mapper.readTree(turns.get(3).content());
        assertEquals("ClassForge", recentAnswer.get("authorDisplayName").asString());
        assertEquals(secondAnswer, recentAnswer.get("content").asString());
        assertEquals("user", turns.getLast().role());
        assertEquals(activeQuestion, turns.getLast().content());
        assertTrue(mapper.writeValueAsString(turns).length() <= 18000);
        assertFalse(turns.stream().anyMatch(turn -> turn.content().contains("pregunta-anterior-1") || turn.content().contains("respuesta-anterior-1")));
    }

    @Test
    void oversizedRecentHistoryCanBeOmittedWhileMandatoryContextStillProducesARequest() {
        String activeQuestion = "Explica el proyecto actual";
        var context = context(document("Cliente"), List.of(message(1, "ASSISTANT", "ClassForge", padded("respuesta-muy-larga", 20000))));

        var turns = assertDoesNotThrow(() -> new ProjectAssistantConversationSupport(mapper, 18000)
                .conversation(context, activeQuestion, AssistantMode.ASK), "Optional history must not block mandatory context");

        assertEquals(3, turns.size());
        assertEquals(activeQuestion, turns.getLast().content());
        assertEquals("Cliente", mapper.readTree(turns.get(1).content()).get("document").get("umlModel").get("classes").get(0).get("name").asString());
    }

    @Test
    void oversizedMandatoryDocumentStillFailsClearlyInsteadOfSendingPartialJson() {
        var context = context(document(padded("Clase-demasiado-grande", 20000)), List.of());

        var error = assertThrows(ProjectAssistantUnavailableException.class,
                () -> new ProjectAssistantConversationSupport(mapper, 18000).conversation(context, "Pregunta", AssistantMode.REVIEW));

        assertTrue(error.getMessage().contains("contexto"));
        assertTrue(error.getMessage().contains("18000"));
    }

    private ProjectDocument document(String name) {
        return new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(UUID.randomUUID(), name, List.of())), List.of()), null);
    }

    private ProjectAssistantContext context(ProjectDocument document, List<ProjectAiMessage> history) {
        return new ProjectAssistantContext(projectId, "Ventas", 7L, document,
                List.of(new ProjectAssistantContext.Member(userId, "Ana", "OWNER")), history);
    }

    private ProjectAiMessage message(long sequence, String role, String name, String content) {
        return new ProjectAiMessage(UUID.randomUUID(), sequence, projectId, UUID.randomUUID(),
                "USER".equals(role) ? userId : null, name, role, AssistantMode.ASK, content, 7L, Instant.now(), null, null);
    }

    private String padded(String prefix, int length) { return prefix + "x".repeat(length - prefix.length()); }
}
