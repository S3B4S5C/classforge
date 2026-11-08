package com.classforge.activity;

import com.classforge.auth.persistence.*;
import com.classforge.auth.security.JwtService;
import com.classforge.collaboration.application.*;
import com.classforge.collaboration.protocol.*;
import com.classforge.integration.xmi.XmiInterchangeService;
import com.classforge.project.application.*;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.*;
import com.classforge.project.persistence.*;
import com.classforge.project.validation.ProjectDocumentValidationException;
import com.classforge.session.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.database=activity_integration",
        "classforge.activity.sessions.maintenance-enabled=false",
        "classforge.activity.sessions.inactivity-timeout=PT30M"
})
class ProjectActivityIntegrationTests {
    @LocalServerPort int port;
    @Autowired ProjectService projects;
    @Autowired ProjectCollaborationService collaboration;
    @Autowired ProjectActivityQueryService activities;
    @Autowired ProjectSessionService sessions;
    @Autowired ProjectActivityRepository activityRepository;
    @Autowired ProjectSessionRepository sessionRepository;
    @Autowired ProjectSessionParticipantRepository participantRepository;
    @Autowired UserRepository users;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired XmiInterchangeService xmi;
    @MockitoBean(name = "projectActivityClock") Clock clock;
    final HttpClient http = HttpClient.newHttpClient();
    final Instant start = Instant.parse("2026-11-08T12:00:00Z");
    UserEntity owner, editor, outsider;
    Project project;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(start);
        owner = user("Sebastián"); editor = user("Marco"); outsider = user("Externo");
        project = projects.create(owner.getId(), "Trazabilidad");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
    }
    UserEntity user(String name) {
        UUID id = UUID.randomUUID();
        return users.save(new UserEntity(id, name, id + "@test.invalid", "unused", start));
    }
    UmlCommandPayload command(UmlCommandType type, Map<String,Object> fields) {
        var data = new HashMap<String,Object>(fields);
        data.put("type", type); data.put("commandId", UUID.randomUUID()); data.put("issuedAt", start);
        return json.convertValue(data, UmlCommandPayload.class);
    }
    UmlCommandPayload create(String name) {
        return command(UmlCommandType.CREATE_CLASS, Map.of("umlClass", new UmlClass(UUID.randomUUID(), name, List.of()),
                "layout", new DiagramNodeLayout(10, 10, 260, 160)));
    }
    ProjectOperationRequest request(long revision, UmlCommandPayload command, ActivitySource source) {
        return new ProjectOperationRequest(UUID.randomUUID(), project.id(), UUID.randomUUID(), revision, command, source);
    }
    void apply(UUID actor, long revision, UmlCommandPayload command) { collaboration.apply(actor, project.id(), request(revision, command, null)); }
    List<ProjectActivity> history() { return activities.find(owner.getId(), project.id(), ProjectActivityFilter.all(), 0, 100).items(); }
    ProjectSession session() { return sessions.getCurrentOrLastSession(owner.getId(), project.id()).orElseThrow(); }
    HttpResponse<String> http(UserEntity user, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/projects/" + project.id() + path))
                .header("Content-Type", "application/json");
        if (user != null) builder.header("Authorization", "Bearer " + jwt.issue(user));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void acceptedOperationPersistsExactlyOneCorrelatedActivityAndSnapshots() {
        var command = create("Cliente");
        var request = request(0, command, ActivitySource.ASSISTANT_TEXT);
        var applied = collaboration.apply(editor.getId(), project.id(), request);
        var item = history().getFirst();
        assertEquals(1, history().size()); assertEquals(project.id(), item.projectId());
        assertEquals(applied.operationId(), item.operationId()); assertEquals(editor.getId(), item.actorUserId());
        assertEquals("Marco", item.actorDisplayName()); assertEquals(0, item.revisionBefore()); assertEquals(1, item.revisionAfter());
        assertEquals(ActivitySource.ASSISTANT_TEXT, item.source()); assertEquals(ActivityOperationType.CREATE_CLASS, item.operationType());
        assertEquals(start, item.createdAt()); assertEquals("creó la clase Cliente", item.description());
        var payload = json.readValue(item.payloadJson(), ProjectActivityPayload.class);
        assertEquals(command, payload.command()); assertEquals(project.document(), payload.beforeDocument());
        assertEquals(projects.get(owner.getId(), project.id()).document(), payload.afterDocument());
        assertEquals(item.sessionId(), session().id()); assertEquals(1, payload.changes().size());
        assertEquals(item, activities.get(editor.getId(), project.id(), item.id()));
    }

    @Test void rejectedStaleInvalidUnauthorizedAndFailedBatchProduceNoActivityOrSession() {
        assertThrows(ProjectNotFoundException.class, () -> apply(outsider.getId(), 0, create("Intruso")));
        assertThrows(ProjectOperationRejectedException.class, () -> apply(owner.getId(), 5, create("Desfasado")));
        var first = create("Duplicada");
        assertThrows(ProjectOperationRejectedException.class, () -> apply(owner.getId(), 0,
                UmlCommandPayload.batch("Inválido", List.of(first, create("Duplicada")))));
        assertTrue(history().isEmpty()); assertTrue(sessions.getCurrentOrLastSession(owner.getId(), project.id()).isEmpty());
        assertEquals(0, projects.get(owner.getId(), project.id()).revision());
    }

    @Test void outerRollbackRemovesDocumentActivitySessionAndParticipantsTogether() {
        var tx = new TransactionTemplate(transactions);
        UUID[] sessionId = new UUID[1];
        tx.executeWithoutResult(status -> {
            apply(owner.getId(), 0, create("Temporal"));
            sessionId[0] = session().id(); assertEquals(1, history().size());
            status.setRollbackOnly();
        });
        assertEquals(0, projects.get(owner.getId(), project.id()).revision()); assertTrue(history().isEmpty());
        assertFalse(sessionRepository.existsById(sessionId[0])); assertTrue(participantRepository.findBySessionIdOrderByUserIdAsc(sessionId[0]).isEmpty());
    }

    @Test void auditConstraintFailureRollsBackDocumentAndSessionCounters() {
        var first = request(0, create("Primera"), null);
        collaboration.apply(owner.getId(), project.id(), first);
        var duplicateId = new ProjectOperationRequest(first.operationId(), project.id(), UUID.randomUUID(), 1, create("Segunda"));
        assertThrows(DataIntegrityViolationException.class, () -> collaboration.apply(editor.getId(), project.id(), duplicateId));
        assertEquals(1, projects.get(owner.getId(), project.id()).revision()); assertEquals(1, history().size());
        assertEquals(1, session().totalOperations()); assertEquals(1, session().participants().size());
    }

    @Test void batchKeepsOneActivityButSummarizesEveryOrderedCommand() {
        var created = create("Animal"); UUID id = created.umlClass().id();
        apply(owner.getId(), 0, UmlCommandPayload.batch("Crear y renombrar", List.of(created,
                command(UmlCommandType.RENAME_CLASS, Map.of("classId", id, "name", "Mascota")),
                command(UmlCommandType.DELETE_CLASS, Map.of("classId", id)))));
        var item = history().getFirst(); var payload = json.readValue(item.payloadJson(), ProjectActivityPayload.class);
        assertEquals(1, history().size()); assertEquals(ActivityOperationType.BATCH, item.operationType());
        assertEquals(List.of("creó la clase Animal", "renombró la clase Animal a Mascota", "eliminó la clase Mascota"),
                payload.changes().stream().map(ActivityChange::description).toList());
        assertEquals(3, session().totalChanges()); assertEquals(1, session().totalOperations());
        assertEquals(Map.of("CREATE_CLASS", 1L, "RENAME_CLASS", 1L, "DELETE_CLASS", 1L), session().operationCounts());
    }

    @Test void fullDocumentSaveIsAuditedIncludingSemanticDifferencesAndNoOpSaves() {
        var c = new UmlClass(UUID.randomUUID(), "Factura", List.of(new UmlAttribute(UUID.randomUUID(), "id", UmlDataType.LONG, null, UmlVisibility.PRIVATE, false, true)));
        var doc = new ProjectDocument("1.0", new UmlModel(List.of(c), List.of()), DiagramLayout.empty());
        UUID operation = UUID.randomUUID();
        projects.saveDocument(editor.getId(), project.id(), 0, doc, ActivitySource.VISION, operation);
        var item = history().getFirst();
        assertEquals(operation, item.operationId()); assertEquals(ActivitySource.VISION, item.source());
        assertEquals(ActivityOperationType.SAVE_DOCUMENT, item.operationType());
        assertEquals(2, json.readValue(item.payloadJson(), ProjectActivityPayload.class).changes().size());
        projects.saveDocument(owner.getId(), project.id(), 1, doc);
        assertEquals(2, history().size()); assertEquals(ActivitySource.MANUAL, history().getFirst().source());
        assertEquals(2, session().totalChanges()); assertEquals(2, session().totalOperations());
        assertTrue(history().getFirst().description().contains("sin diferencias"));
    }

    @Test void invalidOrConflictingFullSaveDoesNotAudit() {
        assertThrows(ProjectRevisionConflictException.class, () -> projects.saveDocument(owner.getId(), project.id(), 4, ProjectDocument.empty()));
        var duplicate = new UmlClass(UUID.randomUUID(), "Duplicada", List.of());
        var invalid = new ProjectDocument("1.0", new UmlModel(List.of(duplicate, duplicate), List.of()), DiagramLayout.empty());
        assertThrows(ProjectDocumentValidationException.class, () -> projects.saveDocument(owner.getId(), project.id(), 0, invalid));
        assertTrue(history().isEmpty()); assertTrue(sessions.getCurrentOrLastSession(owner.getId(), project.id()).isEmpty());
    }

    @Test void xmiPreviewIsReadOnlyAndApplyRecordsActualOriginAndPreviewCorrelation() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmi:version="2.1">
                  <uml:Model xmi:id="model" name="Demo"><packagedElement xmi:type="uml:Class" xmi:id="cliente" name="Cliente"/></uml:Model>
                </xmi:XMI>
                """;
        var preview = xmi.preview(owner.getId(), project.id(), new MockMultipartFile("file", "model.xmi", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(history().isEmpty());
        xmi.apply(owner.getId(), project.id(), preview.previewToken());
        assertEquals(ActivitySource.XMI, history().getFirst().source()); assertEquals(preview.previewToken(), history().getFirst().operationId());
    }

    @Test void allOriginsRoundTripAndLegacyRequestsDefaultToManual() {
        int revision = 0;
        for (var source : ActivitySource.values()) {
            collaboration.apply(owner.getId(), project.id(), request(revision++, create("Clase" + revision), source));
            assertEquals(source, history().getFirst().source());
        }
        var oldRequest = json.readValue(json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "projectId", project.id(),
                "clientId", UUID.randomUUID(), "baseRevision", revision, "command", create("Legacy"))), ProjectOperationRequest.class);
        collaboration.apply(owner.getId(), project.id(), oldRequest);
        assertEquals(ActivitySource.MANUAL, history().getFirst().source());
    }

    @Test void sessionsPersistBothParticipantsAndUpdateSummariesOnEveryOperation() {
        apply(owner.getId(), 0, create("Cliente"));
        var first = session(); when(clock.instant()).thenReturn(start.plusSeconds(60));
        apply(editor.getId(), 1, create("Pedido"));
        var session = session();
        assertEquals(first.id(), session.id()); assertEquals(ProjectSessionStatus.OPEN, session.status()); assertNull(session.endedAt());
        assertEquals(start, session.startedAt()); assertEquals(start.plusSeconds(60), session.lastActivityAt());
        assertEquals(0, session.startRevision()); assertEquals(2, session.endRevision());
        assertEquals(Set.of(owner.getId(), editor.getId()), session.participants().stream().map(ProjectSession.Participant::userId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(session.summary().contains("Clases creadas: 2")); assertTrue(session.summary().contains("Sebastián")); assertTrue(session.summary().contains("Marco"));
        assertEquals(session, sessions.get(editor.getId(), project.id(), session.id()));
    }

    @Test void explicitCloseIsIdempotentAndNextActivityOpensANewSession() {
        apply(owner.getId(), 0, create("Cliente")); var first = session();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        var closed = sessions.close(editor.getId(), project.id(), first.id());
        assertEquals(ProjectSessionStatus.CLOSED, closed.status()); assertEquals("EXPLICIT", closed.closeReason());
        assertEquals(start.plusSeconds(60), closed.endedAt()); assertTrue(closed.summary().contains("CLOSED"));
        when(clock.instant()).thenReturn(start.plusSeconds(120));
        assertEquals(closed, sessions.close(owner.getId(), project.id(), first.id()));
        apply(owner.getId(), 1, create("Pedido"));
        assertNotEquals(first.id(), session().id()); assertEquals(1, session().startRevision()); assertEquals(2, session().endRevision());
        assertEquals(1, sessions.get(owner.getId(), project.id(), first.id()).totalOperations());
        assertEquals(1, sessions.list(owner.getId(), project.id(), ProjectSessionStatus.OPEN, 0, 20).totalElements());
        assertEquals(1, sessions.list(owner.getId(), project.id(), ProjectSessionStatus.CLOSED, 0, 20).totalElements());
    }

    @Test void inactivityBoundaryClosesOldSessionAndGroupsNewActivitySeparately() {
        apply(owner.getId(), 0, create("Cliente")); var first = session();
        when(clock.instant()).thenReturn(start.plusSeconds(1799)); apply(editor.getId(), 1, create("Pedido"));
        assertEquals(first.id(), session().id());
        when(clock.instant()).thenReturn(start.plusSeconds(3599)); apply(owner.getId(), 2, create("Factura"));
        assertNotEquals(first.id(), session().id());
        var closed = sessions.get(owner.getId(), project.id(), first.id());
        assertEquals("INACTIVITY", closed.closeReason()); assertEquals(start.plusSeconds(3599), closed.endedAt());
        assertEquals(2, closed.endRevision()); assertEquals(2, closed.totalOperations()); assertEquals(2, session().startRevision());
    }

    @Test void maintenanceClosesWithoutAnotherOperationAndRechecksRecentActivity() {
        apply(owner.getId(), 0, create("Cliente")); var first = session();
        when(clock.instant()).thenReturn(start.plusSeconds(1800));
        assertTrue(sessions.inactiveProjects().contains(project.id()));
        sessions.closeIfInactive(project.id());
        assertEquals(ProjectSessionStatus.CLOSED, session().status()); assertEquals(start.plusSeconds(1800), session().endedAt());
        apply(owner.getId(), 1, create("Pedido")); var next = session();
        sessions.closeIfInactive(project.id());
        assertEquals(ProjectSessionStatus.OPEN, session().status()); assertNotEquals(first.id(), next.id());
    }

    @Test void activityFiltersAreCombinedScopedAndPaginatedInRevisionOrder() {
        apply(owner.getId(), 0, create("Cliente"));
        when(clock.instant()).thenReturn(start.plusSeconds(30)); apply(editor.getId(), 1, create("Pedido"));
        when(clock.instant()).thenReturn(start.plusSeconds(60)); apply(editor.getId(), 2, create("Factura"));
        var filter = new ProjectActivityFilter(editor.getId(), start.plusSeconds(30), start.plusSeconds(60), 1L, 3L, ActivityOperationType.CREATE_CLASS, session().id());
        var page = activities.find(owner.getId(), project.id(), filter, 0, 1);
        assertEquals(1, page.totalElements()); assertEquals(2, page.items().getFirst().revisionAfter());
        var first = activities.find(owner.getId(), project.id(), ProjectActivityFilter.all(), 0, 2);
        var second = activities.find(owner.getId(), project.id(), ProjectActivityFilter.all(), 1, 2);
        assertEquals(List.of(3L, 2L), first.items().stream().map(ProjectActivity::revisionAfter).toList());
        assertEquals(List.of(1L), second.items().stream().map(ProjectActivity::revisionAfter).toList());
        assertEquals(3, first.totalElements()); assertEquals(2, first.totalPages());
        assertEquals(2, activities.getActivityByActor(owner.getId(), project.id(), editor.getId()).totalElements());
        assertEquals(1, activities.getSinceRevision(owner.getId(), project.id(), 2).totalElements());
        assertEquals(2, activities.findSince(owner.getId(), project.id(), start.plusSeconds(30)).totalElements());
        assertEquals(1, activities.findBetween(owner.getId(), project.id(), start, start).totalElements());
    }

    @Test void aggregateContextUsesAllMatchingRowsRatherThanTheFirstPage() {
        apply(owner.getId(), 0, create("Cliente")); apply(editor.getId(), 1, create("Pedido")); apply(editor.getId(), 2, create("Factura"));
        for (int revision = 3; revision < 23; revision++) apply(editor.getId(), revision, create("Clase" + revision));
        assertEquals(20, activities.getRecentActivity(owner.getId(), project.id()).items().size());
        var total = activities.summarize(editor.getId(), project.id(), ProjectActivityFilter.all());
        assertEquals(23, total.totalChanges()); assertEquals(0L, total.fromRevision()); assertEquals(23L, total.toRevision());
        assertEquals(2, total.participants().size());
        assertEquals(22, total.participants().stream().filter(p -> p.userId().equals(editor.getId())).findFirst().orElseThrow().changes());
        var empty = activities.summarize(owner.getId(), project.id(), new ProjectActivityFilter(outsider.getId(), null, null, null, null, null, null));
        assertEquals(0, empty.totalChanges()); assertNull(empty.fromRevision()); assertNull(empty.toRevision()); assertTrue(empty.participants().isEmpty());
    }

    @Test void renamedUsersKeepHistoricalAttributionAndAggregatesGroupByIdentity() {
        apply(owner.getId(), 0, create("Cliente"));
        users.save(new UserEntity(owner.getId(), "Nombre actualizado", owner.getEmail(), "unused", start));
        apply(owner.getId(), 1, create("Pedido"));
        var records = history();
        assertEquals("Sebastián", records.getLast().actorDisplayName());
        assertEquals("Nombre actualizado", records.getFirst().actorDisplayName());
        var summary = activities.summarize(owner.getId(), project.id(), ProjectActivityFilter.all());
        assertEquals(1, summary.participants().size()); assertEquals(2, summary.participants().getFirst().changes());
        assertEquals("Nombre actualizado", summary.participants().getFirst().displayName());
        assertEquals("Nombre actualizado", session().participants().getFirst().displayName());
    }

    @Test void sessionOrderingIsStableWhenExplicitCloseAndNewActivityShareATimestamp() {
        apply(owner.getId(), 0, create("Cliente")); var first = session();
        sessions.close(owner.getId(), project.id(), first.id());
        apply(owner.getId(), 1, create("Pedido")); var second = session();
        assertNotEquals(first.id(), second.id()); assertEquals(start, second.startedAt());
        assertEquals(ProjectSessionStatus.OPEN, second.status());
        assertEquals(second.id(), sessions.list(owner.getId(), project.id(), null, 0, 20).items().getFirst().id());
    }

    @Test void httpEndpointsSupportFiltersDetailSummarySessionActivitiesAndClose() throws Exception {
        apply(owner.getId(), 0, create("Cliente")); var s = session(); var a = history().getFirst();
        var list = http(editor, "GET", "/activities?actorId=" + owner.getId() + "&afterRevision=0&beforeRevision=2&operationType=CREATE_CLASS&size=1&sessionId=" + s.id(), null);
        assertEquals(200, list.statusCode(), list.body()); assertEquals(1, json.readTree(list.body()).get("totalElements").asLong());
        assertEquals(200, http(editor, "GET", "/activities/" + a.id(), null).statusCode());
        assertEquals(200, http(editor, "GET", "/activities/summary", null).statusCode());
        assertEquals(200, http(editor, "GET", "/sessions?status=OPEN&size=1", null).statusCode());
        assertEquals(200, http(editor, "GET", "/sessions/" + s.id(), null).statusCode());
        assertEquals(200, http(editor, "GET", "/sessions/current-or-last", null).statusCode());
        assertEquals(200, http(editor, "POST", "/sessions/" + s.id() + "/close", null).statusCode());
        assertEquals(ProjectSessionStatus.CLOSED, session().status());
    }

    @Test void httpRejectsInvalidParametersAndUnauthenticatedRequests() throws Exception {
        for (String query : List.of("size=0", "size=101", "page=-1", "afterRevision=-1", "afterRevision=5&beforeRevision=4", "operationType=UNKNOWN", "from=bad-date", "from=2026-11-09T00:00:00Z&to=2026-11-08T00:00:00Z")) {
            assertEquals(400, http(owner, "GET", "/activities?" + query, null).statusCode(), query);
        }
        assertEquals(400, http(owner, "GET", "/sessions?size=0", null).statusCode());
        assertEquals(400, http(owner, "GET", "/sessions?status=UNKNOWN", null).statusCode());
        assertEquals(401, http(null, "GET", "/activities", null).statusCode());
        assertEquals(204, http(owner, "GET", "/sessions/current-or-last", null).statusCode());
    }

    @Test void outsidersAndCrossProjectIdentifiersCannotReadOrCloseAnything() throws Exception {
        apply(owner.getId(), 0, create("Cliente")); var s = session(); var a = history().getFirst();
        for (String path : List.of("/activities", "/activities/" + a.id(), "/activities/summary", "/sessions", "/sessions/" + s.id(), "/sessions/current-or-last")) {
            assertEquals(404, http(outsider, "GET", path, null).statusCode(), path);
        }
        assertEquals(404, http(outsider, "POST", "/sessions/" + s.id() + "/close", null).statusCode());
        Project original = project; project = projects.create(owner.getId(), "Otro");
        assertEquals(0, json.readTree(http(owner, "GET", "/activities", null).body()).get("totalElements").asLong());
        assertEquals(404, http(owner, "GET", "/activities/" + a.id(), null).statusCode());
        assertEquals(404, http(owner, "GET", "/sessions/" + s.id(), null).statusCode());
        assertEquals(404, http(owner, "POST", "/sessions/" + s.id() + "/close", null).statusCode());
        assertEquals(ProjectSessionStatus.OPEN, sessions.get(owner.getId(), original.id(), s.id()).status());
    }

    @Test void documentHttpSaveAcceptsOriginAndOperationIdAndDefaultsLegacyClients() throws Exception {
        UUID op = UUID.randomUUID();
        var response = http(editor, "PUT", "/document", Map.of("baseRevision", 0, "document", ProjectDocument.empty(), "source", "ASSISTANT_VOICE", "operationId", op));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(op, history().getFirst().operationId()); assertEquals(ActivitySource.ASSISTANT_VOICE, history().getFirst().source());
        assertEquals(200, http(owner, "PUT", "/document", Map.of("baseRevision", 1, "document", ProjectDocument.empty())).statusCode());
        assertEquals(ActivitySource.MANUAL, history().getFirst().source());
        assertEquals(400, http(owner, "PUT", "/document", Map.of("baseRevision", 2, "document", ProjectDocument.empty(), "source", "INVALID")).statusCode());
        assertEquals(2, history().size());
    }

    @Test void concurrentWritersSerializeRevisionsAndMaintainASingleOpenSession() throws Exception {
        int writers = 6;
        var ready = new CountDownLatch(writers); var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(writers)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown(); assertTrue(go.await(10, TimeUnit.SECONDS));
                    while (true) {
                        long revision = projects.get(owner.getId(), project.id()).revision();
                        try { apply(index % 2 == 0 ? owner.getId() : editor.getId(), revision, create("Concurrente" + index)); return null; }
                        catch (ProjectOperationRejectedException conflict) { if (!conflict.getCode().equals("REVISION_CONFLICT")) throw conflict; }
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertEquals(writers, history().size()); assertEquals(writers, session().totalOperations());
        assertEquals(1, sessions.list(owner.getId(), project.id(), ProjectSessionStatus.OPEN, 0, 20).totalElements());
        assertEquals(2, session().participants().size());
        assertEquals(java.util.stream.LongStream.rangeClosed(1, writers).boxed().collect(java.util.stream.Collectors.toSet()),
                history().stream().map(ProjectActivity::revisionAfter).collect(java.util.stream.Collectors.toSet()));
    }
}
