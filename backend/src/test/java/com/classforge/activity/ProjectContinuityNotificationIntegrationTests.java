package com.classforge.activity;

import com.classforge.auth.persistence.*;
import com.classforge.auth.security.JwtService;
import com.classforge.collaboration.application.*;
import com.classforge.collaboration.protocol.*;
import com.classforge.continuity.*;
import com.classforge.notification.*;
import com.classforge.project.application.*;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.*;
import com.classforge.project.persistence.*;
import com.classforge.session.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectContinuityNotificationIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired ProjectService projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired ProjectCollaborationService collaboration;
    @Autowired ProjectActivityQueryService activities;
    @Autowired ProjectContinuityService continuity;
    @Autowired ProjectNotificationService notifications;
    @Autowired ProjectSessionService sessions;
    @Autowired MongoTemplate mongo;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry registry;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    @MockitoBean(name = "projectActivityClock") Clock clock;
    @MockitoSpyBean SimpMessagingTemplate messaging;
    UserEntity owner, editor, outsider;
    Project project;
    final Instant now = Instant.parse("2026-11-08T12:00:00Z");
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        owner = user("Sebastián"); editor = user("Marco"); outsider = user("Externo");
        project = projects.create(owner.getId(), "Continuidad");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId()));
    }
    UserEntity user(String name) { UUID id = UUID.randomUUID(); return users.save(new UserEntity(id, name, id + "@test.invalid", "unused", now)); }
    void change(UserEntity user, long revision, String name) {
        var command = new UmlCommandPayload(UUID.randomUUID(), now, UmlCommandType.CREATE_CLASS, null, null,
                new UmlClass(UUID.randomUUID(), name, List.of()), new DiagramNodeLayout(10, 10, 260, 160), null, null, null, null);
        collaboration.apply(user.getId(), project.id(), new ProjectOperationRequest(UUID.randomUUID(), project.id(), UUID.randomUUID(), revision, command));
    }
    List<ProjectNotification> inbox(UserEntity user) { return notifications.list(user.getId(), null, false, 0, 100).items(); }
    HttpResponse<String> request(UserEntity user, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (user != null) builder.header("Authorization", "Bearer " + jwt.issue(user));
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    String projectPath(String suffix) { return "/api/projects/" + project.id() + suffix; }

    @Test void readingContinuityDoesNotConsumeNewActivityOrCreateAVisit() {
        change(editor, 0, "Cliente");
        var first = continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20);
        assertTrue(first.firstVisit()); assertEquals(1, first.newActivityCount()); assertEquals(0, first.lastSeenRevision());
        assertEquals(first, continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20));
        assertNull(mongo.findById(ProjectUserVisit.key(project.id(), owner.getId()), ProjectUserVisit.class));
    }
    @Test void acknowledgedRevisionSeparatesOfflineChangesAndKeepsAuthorAndSessionContext() {
        change(owner, 0, "Cliente"); continuity.acknowledge(owner.getId(), project.id(), 1);
        when(clock.instant()).thenReturn(now.plusSeconds(60)); change(editor, 1, "Pedido"); change(editor, 2, "Factura");
        var result = continuity.sinceLastVisit(owner.getId(), project.id(), 0, 1);
        assertFalse(result.firstVisit()); assertEquals(1, result.lastSeenRevision()); assertEquals(3, result.currentRevision());
        assertEquals(2, result.newActivityCount()); assertEquals(1, result.activities().size()); assertTrue(result.hasMore());
        assertEquals(editor.getId(), result.participants().getFirst().userId()); assertEquals(2, result.participants().getFirst().changes());
        assertEquals(2, continuity.sinceLastVisit(owner.getId(), project.id(), 1, 1).activities().getFirst().revisionAfter());
    }
    @Test void acknowledgementNeverMovesBackwardsOrAcknowledgesChangesArrivingAfterTheRead() {
        change(editor, 0, "Cliente");
        var read = continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20);
        change(editor, 1, "Pedido"); continuity.acknowledge(owner.getId(), project.id(), read.currentRevision());
        var result = continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20);
        assertEquals(1, result.newActivityCount()); assertEquals(2, result.activities().getFirst().revisionAfter());
        continuity.acknowledge(owner.getId(), project.id(), 0); assertEquals(1, continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20).lastSeenRevision());
        assertThrows(IllegalArgumentException.class, () -> continuity.acknowledge(owner.getId(), project.id(), 3));
        assertThrows(IllegalArgumentException.class, () -> continuity.acknowledge(owner.getId(), project.id(), -1));
    }
    @Test void visitStateIsIsolatedByUserAndProject() {
        change(owner, 0, "Cliente"); continuity.acknowledge(owner.getId(), project.id(), 1);
        assertEquals(0, continuity.sinceLastVisit(owner.getId(), project.id(), 0, 20).newActivityCount());
        assertEquals(1, continuity.sinceLastVisit(editor.getId(), project.id(), 0, 20).newActivityCount());
        var other = projects.create(owner.getId(), "Otro"); assertTrue(continuity.sinceLastVisit(owner.getId(), other.id(), 0, 20).firstVisit());
        assertThrows(ProjectNotFoundException.class, () -> continuity.sinceLastVisit(outsider.getId(), project.id(), 0, 20));
        assertThrows(ProjectNotFoundException.class, () -> continuity.acknowledge(outsider.getId(), project.id(), 0));
    }
    @Test void continuityHttpValidatesAccessRevisionAndPagination() throws Exception {
        change(editor, 0, "Cliente");
        var response = request(owner, "GET", projectPath("/since-last-visit?size=1"), null);
        assertEquals(200, response.statusCode(), response.body()); assertEquals(1, json.readTree(response.body()).get("newActivityCount").asLong());
        assertEquals(200, request(owner, "POST", projectPath("/visits"), Map.of("revision", 1)).statusCode());
        assertEquals(400, request(owner, "POST", projectPath("/visits"), Map.of("revision", 2)).statusCode());
        assertEquals(400, request(owner, "POST", projectPath("/visits"), Map.of("revision", -1)).statusCode());
        assertEquals(400, request(owner, "GET", projectPath("/since-last-visit?size=0"), null).statusCode());
        assertEquals(404, request(outsider, "GET", projectPath("/since-last-visit"), null).statusCode());
        assertEquals(401, request(null, "GET", projectPath("/since-last-visit"), null).statusCode());
    }
    @Test void activityNotifiesAllOtherMembersIncludingOwnerAndNeverTheAuthor() {
        var third = user("Tercero"); memberships.save(ProjectMembershipEntity.editor(project.id(), third.getId()));
        change(editor, 0, "Factura");
        assertEquals(1, inbox(owner).size()); assertEquals(1, inbox(third).size()); assertTrue(inbox(editor).isEmpty()); assertTrue(inbox(outsider).isEmpty());
        var notification = inbox(owner).getFirst();
        assertEquals(project.id(), notification.projectId()); assertEquals(owner.getId(), notification.recipientUserId());
        assertEquals(ProjectNotification.Type.PROJECT_ACTIVITY, notification.type()); assertEquals("Marco creó la clase Factura", notification.message());
        assertEquals(activities.getRecentActivity(owner.getId(), project.id()).items().getFirst().id(), notification.activityId());
        assertFalse(notification.read()); assertEquals(1, notifications.unreadCount(owner.getId()));
    }
    @Test void rolledBackOrRejectedChangesNeverPersistOrBroadcastNotifications() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { change(editor, 0, "Temporal"); tx.setRollbackOnly(); });
        assertTrue(inbox(owner).isEmpty()); assertEquals(0, notifications.unreadCount(owner.getId()));
        assertThrows(ProjectOperationRejectedException.class, () -> change(editor, 9, "Desfasado"));
        verify(messaging, never()).convertAndSendToUser(eq(owner.getId().toString()), eq("/queue/notifications"), any(Object.class));
    }
    @Test void notificationRemainsDurableWhenRealtimeDeliveryFails() {
        doThrow(new IllegalStateException("Broker temporalmente no disponible")).when(messaging)
                .convertAndSendToUser(eq(owner.getId().toString()), eq("/queue/notifications"), any(Object.class));
        change(editor, 0, "Persistente");
        assertEquals(1, inbox(owner).size()); assertEquals(1, projects.get(owner.getId(), project.id()).revision());
        assertEquals(1, notifications.unreadCount(owner.getId()));
    }
    @Test void replayingTheSameActivityEventDoesNotDuplicateNotifications() {
        change(editor, 0, "Cliente"); var activity = activities.getRecentActivity(owner.getId(), project.id()).items().getFirst();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> events.publishEvent(new ProjectActivityRecordedEvent(activity)));
        assertEquals(1, inbox(owner).size());
    }
    @Test void markingOneNotificationReadIsIdempotentAndCannotAffectAnotherRecipient() {
        change(editor, 0, "Cliente"); var notification = inbox(owner).getFirst();
        when(clock.instant()).thenReturn(now.plusSeconds(90));
        var first = notifications.markRead(owner.getId(), notification.id());
        assertTrue(first.read()); assertEquals(now.plusSeconds(90), first.readAt()); assertEquals(0, notifications.unreadCount(owner.getId()));
        when(clock.instant()).thenReturn(now.plusSeconds(180)); assertEquals(first, notifications.markRead(owner.getId(), notification.id()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> notifications.markRead(editor.getId(), notification.id()));
    }
    @Test void markAllReadAndProjectFilterAreScopedToTheAuthenticatedRecipient() {
        change(editor, 0, "Cliente"); change(owner, 1, "Pedido");
        var firstProject = project; project = projects.create(owner.getId(), "Otro");
        memberships.save(ProjectMembershipEntity.editor(project.id(), editor.getId())); change(editor, 0, "Factura");
        assertEquals(2, notifications.unreadCount(owner.getId()));
        assertEquals(1, notifications.markAllRead(owner.getId(), firstProject.id())); assertEquals(1, notifications.unreadCount(owner.getId()));
        assertEquals(1, notifications.unreadCount(editor.getId()));
        assertEquals(1, notifications.list(owner.getId(), null, true, 0, 20).totalElements());
        assertEquals(1, notifications.markAllRead(owner.getId(), null)); assertEquals(0, notifications.markAllRead(owner.getId(), null));
        assertEquals(1, notifications.unreadCount(editor.getId()));
    }
    @Test void sessionCloseNotifiesOtherMembersAndAutomaticClosureIncludesAllMembers() {
        change(owner, 0, "Cliente"); var session = sessions.getCurrentOrLastSession(owner.getId(), project.id()).orElseThrow();
        sessions.close(owner.getId(), project.id(), session.id());
        assertTrue(inbox(owner).isEmpty());
        assertEquals(1, inbox(editor).stream().filter(n -> n.type() == ProjectNotification.Type.SESSION_CLOSED).count());
        sessions.close(owner.getId(), project.id(), session.id()); assertEquals(2, inbox(editor).size());
        change(owner, 1, "Pedido"); when(clock.instant()).thenReturn(now.plusSeconds(1800)); sessions.closeIfInactive(project.id());
        assertEquals(1, inbox(owner).stream().filter(n -> n.type() == ProjectNotification.Type.SESSION_CLOSED).count());
        assertEquals(2, inbox(editor).stream().filter(n -> n.type() == ProjectNotification.Type.SESSION_CLOSED).count());
    }
    @Test void notificationHttpDoesNotExposeAnotherUsersInboxOrReadActions() throws Exception {
        change(editor, 0, "Cliente"); var notification = inbox(owner).getFirst();
        assertEquals(1, json.readTree(request(owner, "GET", "/api/notifications/unread-count", null).body()).get("count").asLong());
        assertEquals(0, json.readTree(request(editor, "GET", "/api/notifications", null).body()).get("totalElements").asLong());
        assertEquals(404, request(editor, "POST", "/api/notifications/" + notification.id() + "/read", Map.of()).statusCode());
        assertEquals(200, request(owner, "POST", "/api/notifications/" + notification.id() + "/read", Map.of()).statusCode());
        assertEquals(200, request(editor, "POST", "/api/notifications/read-all", Map.of()).statusCode());
        assertEquals(400, request(owner, "GET", "/api/notifications?size=101", null).statusCode());
        assertEquals(401, request(null, "GET", "/api/notifications", null).statusCode());
    }
    @Test void twoUsersReceiveOnlyTheirOwnCommittedRealtimeNotifications() throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient()); client.setMessageConverter(new JsonTextConverter());
        StompSession a = null, b = null;
        try {
            a = connect(client, owner); b = connect(client, editor);
            var receivedA = new LinkedBlockingQueue<String>(); var receivedB = new LinkedBlockingQueue<String>();
            a.subscribe("/user/queue/notifications", handler(receivedA)); b.subscribe("/user/queue/notifications", handler(receivedB));
            Thread.sleep(150);
            change(editor, 0, "Cliente");
            assertNotNull(registry.getUser(owner.getId().toString()), "The owner must be registered for private STOMP delivery");
            verify(messaging).convertAndSendToUser(eq(owner.getId().toString()), eq("/queue/notifications"), any(Object.class));
            String payload = receivedA.poll(5, TimeUnit.SECONDS); assertNotNull(payload);
            var event = json.readTree(payload); assertEquals("NOTIFICATION_CREATED", event.get("type").asString());
            assertEquals(owner.getId().toString(), event.get("recipientUserId").asString());
            assertEquals(inbox(owner).getFirst().id().toString(), event.get("notification").get("id").asString());
            assertNull(receivedB.poll(200, TimeUnit.MILLISECONDS));
            notifications.markAllRead(owner.getId(), null);
            assertEquals("NOTIFICATIONS_CHANGED", json.readTree(receivedA.poll(5, TimeUnit.SECONDS)).get("type").asString());
        } finally { if (a != null && a.isConnected()) a.disconnect(); if (b != null && b.isConnected()) b.disconnect(); client.stop(); }
    }
    private StompSession connect(WebSocketStompClient client, UserEntity user) throws Exception {
        var headers = new StompHeaders(); headers.add("Authorization", "Bearer " + jwt.issue(user));
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws", new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }
    private StompFrameHandler handler(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            public void handleFrame(StompHeaders headers, Object payload) { queue.add((String) payload); }
        };
    }
    private static class JsonTextConverter extends StringMessageConverter {
        JsonTextConverter() { addSupportedMimeTypes(org.springframework.util.MimeTypeUtils.APPLICATION_JSON); }
    }
}
