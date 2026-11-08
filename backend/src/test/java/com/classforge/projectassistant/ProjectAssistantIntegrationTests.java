package com.classforge.projectassistant;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.auth.security.JwtService;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.assistant.AssistantPlanService;
import com.classforge.assistant.AssistantPlanResponse;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.database=project_assistant_integration")
class ProjectAssistantIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ProjectService projects;
    @Autowired JsonMapper json;
    @Autowired ProjectMembershipRepository memberships;
    @MockitoBean ProjectAssistantConversationGateway gateway;
    @MockitoBean AssistantPlanService planner;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void configureGateway() {
        when(gateway.respond(any(), anyString(), any())).thenReturn(
                new AssistantConversationResponse("Observación del UML actual.", List.of()));
    }

    UserEntity user() {
        UUID id = UUID.randomUUID();
        return users.save(new UserEntity(id, "Miembro " + id, id + "@test.invalid", "unused", Instant.now()));
    }

    HttpResponse<String> get(UserEntity user, Project project, String query) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port +
                "/api/projects/" + project.id() + "/assistant/messages" + query))
                .header("Authorization", "Bearer " + jwt.issue(user)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpRequest postRequest(UserEntity user, Project project, String text, String mode, UUID requestId) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port +
                "/api/projects/" + project.id() + "/assistant/messages"))
                .header("Authorization", "Bearer " + jwt.issue(user))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("content", text, "mode", mode, "requestId", requestId))))
                .build();
    }

    HttpResponse<String> post(UserEntity user, Project project, String text, String mode, UUID requestId) throws Exception {
        return http.send(postRequest(user, project, text, mode, requestId), HttpResponse.BodyHandlers.ofString());
    }

    @Test void authorizedMemberCanLoadAnEmptyPersistentConversation() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Asistente compartido");
        var response = get(owner, project, "");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, json.readTree(response.body()).get("messages").size());
        assertFalse(json.readTree(response.body()).get("hasMore").asBoolean());
    }

    @Test void ownerAndEditorSharePersistedMessagesAndOtherProjectsAreIsolated() throws Exception {
        UserEntity owner = user(), editor = user();
        Project project = projects.create(owner.getId(), "Compartido");
        Project other = projects.create(owner.getId(), "Otro");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
        var sent = post(owner, project, "¿Qué representa UML?", "ASK", UUID.randomUUID());
        assertEquals(200, sent.statusCode(), sent.body());
        var response = json.readTree(sent.body());
        assertEquals(owner.getId().toString(), response.get("userMessage").get("authorUserId").asString());
        assertEquals("ASSISTANT", response.get("assistantMessage").get("role").asString());
        assertTrue(response.get("assistantMessage").get("plan").isNull());
        assertEquals(2, json.readTree(get(editor, project, "").body()).get("messages").size());
        assertEquals(get(owner, project, "").body(), get(editor, project, "").body());
        assertEquals(0, json.readTree(get(owner, other, "").body()).get("messages").size());
        assertEquals(200, post(editor, project, "Continúa la explicación", "ASK", UUID.randomUUID()).statusCode());
        assertEquals(4, json.readTree(get(owner, project, "").body()).get("messages").size());
        assertEquals(0, projects.get(owner.getId(), project.id()).revision());
        verifyNoInteractions(planner);
    }

    @Test void unauthorizedUserCannotReadOrWriteAndDoesNotInvokeModel() throws Exception {
        UserEntity owner = user(), stranger = user();
        Project project = projects.create(owner.getId(), "Privado");
        assertEquals(404, get(stranger, project, "").statusCode());
        assertEquals(404, post(stranger, project, "Hola", "ASK", UUID.randomUUID()).statusCode());
        verifyNoInteractions(gateway, planner);
    }

    @Test void reviewAndSummaryRetainRevisionAndNeverSaveUml() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Revisión");
        project = projects.saveDocument(owner.getId(), project.id(), 0, ProjectDocument.empty());
        for (String mode : List.of("REVIEW", "SUMMARY_CURRENT")) {
            var response = post(owner, project, "Revisa el estado", mode, UUID.randomUUID());
            assertEquals(200, response.statusCode(), response.body());
            var body = json.readTree(response.body());
            assertEquals(1, body.get("assistantMessage").get("projectRevision").asLong());
            if (mode.equals("REVIEW")) {
                assertEquals(1, body.get("assistantMessage").get("review").get("projectRevision").asLong());
                assertEquals(owner.getId().toString(), body.get("assistantMessage").get("review").get("requestedBy").asString());
            }
        }
        assertEquals(1, projects.get(owner.getId(), project.id()).revision());
        assertEquals(project.document(), projects.get(owner.getId(), project.id()).document());
        verifyNoInteractions(planner);
    }

    @Test void editDelegatesToExistingPlannerAndUsesItsBaseRevisionWithoutApplying() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Editar");
        var plan = new AssistantPlanResponse("TEXT", "Crear clase", 7, "Propuesta preparada",
                new AssistantSemanticPlan("Propuesta", List.of()), UmlCommandPayload.batch("Propuesta", List.of()));
        when(planner.plan(owner.getId(), project.id(), "Crear clase")).thenReturn(plan);
        var response = post(owner, project, "Crear clase", "EDIT", UUID.randomUUID());
        assertEquals(200, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(7, body.get("projectRevision").asLong());
        assertEquals(7, body.get("assistantMessage").get("projectRevision").asLong());
        assertEquals(7, body.get("assistantMessage").get("plan").get("baseRevision").asLong());
        assertEquals(0, projects.get(owner.getId(), project.id()).revision());
        verifyNoInteractions(gateway);
    }

    @Test void retryReturnsSameResponseAndRejectsRequestIdReuseForDifferentContentOrAuthor() throws Exception {
        UserEntity owner = user(), editor = user();
        Project project = projects.create(owner.getId(), "Idempotente");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
        UUID request = UUID.randomUUID();
        var first = post(owner, project, "Pregunta", "ASK", request);
        var repeat = post(owner, project, "Pregunta", "ASK", request);
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(first.body(), repeat.body());
        assertEquals(409, post(owner, project, "Otra pregunta", "ASK", request).statusCode());
        assertEquals(409, post(editor, project, "Pregunta", "ASK", request).statusCode());
        verify(gateway, times(1)).respond(any(), anyString(), any());
    }

    @Test void providerFailureKeepsQuestionAndRetryDoesNotDuplicateIt() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Recuperación");
        UUID request = UUID.randomUUID();
        when(gateway.respond(any(), anyString(), any()))
                .thenThrow(new ProjectAssistantUnavailableException("Proveedor no disponible"))
                .thenReturn(new AssistantConversationResponse("Recuperado", List.of()));
        assertEquals(503, post(owner, project, "Pregunta", "ASK", request).statusCode());
        assertEquals(1, json.readTree(get(owner, project, "").body()).get("messages").size());
        assertEquals(200, post(owner, project, "Pregunta", "ASK", request).statusCode());
        assertEquals(2, json.readTree(get(owner, project, "").body()).get("messages").size());
    }

    @Test void concurrentRetriesProduceOnlyOneAssistantResponse() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Concurrente");
        UUID request = UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(gateway.respond(any(), anyString(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return new AssistantConversationResponse("Única respuesta", List.of());
        });
        var first = http.sendAsync(postRequest(owner, project, "Pregunta", "ASK", request), HttpResponse.BodyHandlers.ofString());
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(409, post(owner, project, "Pregunta", "ASK", request).statusCode());
        } finally { release.countDown(); }
        assertEquals(200, first.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(2, json.readTree(get(owner, project, "").body()).get("messages").size());
        verify(gateway, times(1)).respond(any(), anyString(), any());
    }

    @Test void paginationIsChronologicalWithAnExclusiveStableCursor() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Páginas");
        post(owner, project, "Primera", "ASK", UUID.randomUUID());
        post(owner, project, "Segunda", "ASK", UUID.randomUUID());
        var page = json.readTree(get(owner, project, "?limit=2").body());
        assertTrue(page.get("hasMore").asBoolean());
        long first = page.get("messages").get(0).get("sequence").asLong();
        assertTrue(first < page.get("messages").get(1).get("sequence").asLong());
        var older = json.readTree(get(owner, project, "?limit=2&before=" + first).body());
        assertFalse(older.get("hasMore").asBoolean());
        assertEquals("Primera", older.get("messages").get(0).get("content").asString());
        assertTrue(older.get("messages").get(1).get("sequence").asLong() < first);
        assertEquals(400, get(owner, project, "?limit=0").statusCode());
        assertEquals(400, get(owner, project, "?limit=101").statusCode());
    }

    @Test void invalidMessagesAreRejectedBeforePersistenceAndModelCalls() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Validación");
        assertEquals(400, post(owner, project, "  ", "ASK", UUID.randomUUID()).statusCode());
        assertEquals(400, post(owner, project, "x".repeat(4001), "ASK", UUID.randomUUID()).statusCode());
        assertEquals(400, post(owner, project, "Hola", "UNKNOWN", UUID.randomUUID()).statusCode());
        assertEquals(0, json.readTree(get(owner, project, "").body()).get("messages").size());
        verifyNoInteractions(gateway, planner);
    }

    @Test void contextContainsSharedMembersAndAtMost20PreviousMessages() throws Exception {
        UserEntity owner = user(), editor = user();
        Project project = projects.create(owner.getId(), "Contexto");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
        for (int i = 0; i < 12; i++) post(owner, project, "Pregunta " + i, "ASK", UUID.randomUUID());
        var context = org.mockito.ArgumentCaptor.forClass(ProjectAssistantContext.class);
        verify(gateway, times(12)).respond(context.capture(), anyString(), any());
        var last = context.getValue();
        assertEquals(20, last.recentMessages().size());
        assertEquals(2, last.members().size());
        assertFalse(last.recentMessages().stream().anyMatch(message -> message.content().equals("Pregunta 11")));
        assertEquals(project.document(), last.document());
        assertEquals(project.revision(), last.currentRevision());
    }

    @Test void simultaneousFirstSubmissionsClaimTheRequestOnlyOnce() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Primer envío concurrente");
        UUID request = UUID.randomUUID();
        var futures = java.util.stream.IntStream.range(0, 8).mapToObj(index -> http.sendAsync(
                postRequest(owner, project, "Una pregunta", "ASK", request), HttpResponse.BodyHandlers.ofString())).toList();
        for (var future : futures) {
            var response = future.get(15, TimeUnit.SECONDS);
            assertTrue(response.statusCode() == 200 || response.statusCode() == 409, response.body());
        }
        assertEquals(2, json.readTree(get(owner, project, "").body()).get("messages").size());
        verify(gateway, times(1)).respond(any(), anyString(), any());
    }

    @Test void responseKeepsCapturedRevisionWhenTheProjectChangesDuringGeneration() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Instantánea");
        when(gateway.respond(any(), anyString(), any())).thenAnswer(invocation -> {
            projects.saveDocument(owner.getId(), project.id(), 0, ProjectDocument.empty());
            return new AssistantConversationResponse("Sobre la revisión capturada", List.of());
        });
        var response = post(owner, project, "Revisa", "REVIEW", UUID.randomUUID());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, json.readTree(response.body()).get("assistantMessage").get("review").get("projectRevision").asLong());
        assertEquals(1, projects.get(owner.getId(), project.id()).revision());
    }

    @Test void reviewFiltersInvalidFindingFieldsAndReferencesBeforePersisting() throws Exception {
        UserEntity owner = user();
        Project project = projects.create(owner.getId(), "Hallazgos");
        when(gateway.respond(any(), anyString(), any())).thenReturn(new AssistantConversationResponse("Recomendaciones", List.of(
                new AssistantReviewFinding("INFO", "GENERAL", "El modelo está vacío", null),
                new AssistantReviewFinding(null, "GENERAL", "Severidad inválida", null),
                new AssistantReviewFinding("WARNING", "RELATIONSHIP", "Referencia inventada", UUID.randomUUID().toString()))));
        var response = post(owner, project, "Revisa", "REVIEW", UUID.randomUUID());
        assertEquals(200, response.statusCode(), response.body());
        var findings = json.readTree(response.body()).get("assistantMessage").get("review").get("findings");
        assertEquals(1, findings.size());
        assertEquals("El modelo está vacío", findings.get(0).get("message").asString());
    }
}
