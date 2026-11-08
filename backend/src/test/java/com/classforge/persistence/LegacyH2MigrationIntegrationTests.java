package com.classforge.persistence;

import com.classforge.activity.*;
import com.classforge.auth.application.AuthService;
import com.classforge.auth.persistence.*;
import com.classforge.auth.web.LoginRequest;
import com.classforge.migration.*;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.document.*;
import com.classforge.projectassistant.*;
import com.classforge.session.ProjectSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class LegacyH2MigrationIntegrationTests {
    @TempDir Path directory;
    @Autowired LegacyH2MigrationService migration;
    @Autowired ProjectService projects;
    @Autowired ProjectActivityQueryService activities;
    @Autowired ProjectSessionService sessions;
    @Autowired ProjectAssistantService assistant;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired MongoTemplate mongo;
    @Autowired JsonMapper json;
    @MockitoBean ProjectAssistantConversationGateway gateway;
    final Instant at = Instant.parse("2026-11-08T12:00:00.123456Z");

    @BeforeEach void isolateLegacySequenceSpaces() {
        if (!mongo.getDb().getName().matches("cf_test_[0-9a-f]{32}")) throw new IllegalStateException("Unsafe migration test database");
        for (String collection : mongo.getCollectionNames()) mongo.remove(new org.springframework.data.mongodb.core.query.Query(), collection);
    }

    record Fixture(Path file, UUID owner, UUID editor, UUID project, UUID session, UUID activity, UUID message,
                   ProjectDocument document, String email, String passwordHash) { }

    @Test void importsEveryLegacyCollectionPreservingIdentityDocumentsPasswordsHistorySessionsAndChat() throws Exception {
        var fixture = fixture(false); byte[] original = Files.readAllBytes(fixture.file());
        var report = migration.migrate(fixture.file()); assertFalse(report.alreadyImported());
        assertEquals(8, report.verifiedCounts().size()); assertEquals(2L, report.verifiedCounts().get("app_users"));
        assertEquals(2L, report.verifiedCounts().get("project_ai_messages"));
        assertArrayEquals(original, Files.readAllBytes(fixture.file()));
        assertArrayEquals(original, Files.readAllBytes(fixture.file().resolveSibling(fixture.file().getFileName() + ".before-mongo.bak")));
        var user = users.findById(fixture.owner()).orElseThrow();
        assertEquals(fixture.passwordHash(), user.getPasswordHash()); assertEquals(fixture.owner(), auth.login(new LoginRequest(fixture.email(), "Migracion123!")).user().id());
        assertEquals(fixture.document(), projects.get(fixture.editor(), fixture.project()).document());
        assertNotNull(mongo.findById(fixture.project(), org.bson.Document.class, "projects").get("document"));
        assertEquals(fixture.activity(), activities.getRecentActivity(fixture.owner(), fixture.project()).items().getFirst().id());
        var session = sessions.get(fixture.owner(), fixture.project(), fixture.session());
        assertEquals(1, session.participants().size()); assertEquals(1, session.totalOperations()); assertEquals(1, session.endRevision());
        var history = assistant.history(fixture.owner(), fixture.project(), null, 20);
        assertEquals(2, history.messages().size()); assertEquals(fixture.message(), history.messages().getFirst().id());
        assertEquals(7, history.messages().getFirst().sequence()); assertEquals(8, history.messages().getLast().sequence());
        when(gateway.respond(any(), anyString(), any())).thenReturn(new AssistantConversationResponse("La conversación continúa.", List.of()));
        var response = assistant.send(fixture.editor(), fixture.project(), new ProjectAssistantMessageRequest("Continúa", AssistantMode.ASK, UUID.randomUUID()));
        assertEquals(9, response.userMessage().sequence()); assertEquals(10, response.assistantMessage().sequence());
    }
    @Test void reimportIsIdempotentAndNeverRevertsNewMongoChanges() throws Exception {
        var fixture = fixture(false); migration.migrate(fixture.file());
        projects.saveDocument(fixture.owner(), fixture.project(), 1, ProjectDocument.empty());
        var repeated = migration.migrate(fixture.file()); assertTrue(repeated.alreadyImported());
        assertEquals(2, projects.get(fixture.owner(), fixture.project()).revision());
        assertEquals(ProjectDocument.empty(), projects.get(fixture.owner(), fixture.project()).document());
        assertEquals(2, activities.getRecentActivity(fixture.owner(), fixture.project()).totalElements());
    }
    @Test void conflictingDestinationAbortsTheWholeImportWithoutOverwritingAnyExistingData() throws Exception {
        var fixture = fixture(false);
        users.save(new UserEntity(fixture.owner(), "Destino existente", fixture.email(), "keep-this-hash", at));
        assertThrows(IllegalStateException.class, () -> migration.migrate(fixture.file()));
        assertEquals("keep-this-hash", users.findById(fixture.owner()).orElseThrow().getPasswordHash());
        assertNull(mongo.findById(fixture.project(), org.bson.Document.class, "projects"));
        assertNull(mongo.findById(fixture.activity(), org.bson.Document.class, "project_activities"));
    }
    @Test void unsupportedLegacySnapshotsFailBeforeAnyMongoDocumentIsInserted() throws Exception {
        var fixture = fixture(true);
        assertThrows(IllegalStateException.class, () -> migration.migrate(fixture.file()));
        assertTrue(users.findById(fixture.owner()).isEmpty());
        assertNull(mongo.findById(fixture.project(), org.bson.Document.class, "projects"));
    }

    Fixture fixture(boolean invalidDocument) throws Exception {
        UUID owner = UUID.randomUUID(), editor = UUID.randomUUID(), project = UUID.randomUUID(), session = UUID.randomUUID(),
                activity = UUID.randomUUID(), request = UUID.randomUUID(), message = UUID.randomUUID();
        String email = owner + "@legacy.test", hash = passwords.encode("Migracion123!");
        Path file = directory.resolve("legacy.mv.db");
        var document = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(UUID.randomUUID(), "Cliente", List.of())), List.of()), DiagramLayout.empty());
        String docJson = invalidDocument ? "{\"schemaVersion\":\"1.0\",\"elements\":[{}]}" : json.writeValueAsString(document);
        String payload = json.writeValueAsString(new ProjectActivityPayload(1, null, ProjectDocument.empty(), document,
                List.of(new ActivityChange(ActivityOperationType.CREATE_CLASS, document.umlModel().classes().getFirst().id(), "creó la clase Cliente"))));
        try (var sql = DriverManager.getConnection("jdbc:h2:file:" + directory.resolve("legacy").toString().replace('\\', '/'), "sa", "")) {
            String[] schema = {
                "CREATE TABLE app_users(id UUID PRIMARY KEY,display_name VARCHAR,email VARCHAR,password_hash VARCHAR,created_at TIMESTAMP(6) WITH TIME ZONE)",
                "CREATE TABLE projects(id UUID PRIMARY KEY,owner_id UUID,name VARCHAR,revision BIGINT,uml_model CLOB,created_at TIMESTAMP(6) WITH TIME ZONE,updated_at TIMESTAMP(6) WITH TIME ZONE)",
                "CREATE TABLE project_memberships(id UUID PRIMARY KEY,project_id UUID,user_id UUID,role VARCHAR,created_at TIMESTAMP(6) WITH TIME ZONE)",
                "CREATE TABLE project_invitations(id UUID PRIMARY KEY,project_id UUID,invited_email VARCHAR,invited_by_user_id UUID,status VARCHAR,created_at TIMESTAMP(6) WITH TIME ZONE,responded_at TIMESTAMP(6) WITH TIME ZONE)",
                "CREATE TABLE project_sessions(id UUID PRIMARY KEY,project_id UUID,open_project_id UUID,started_at TIMESTAMP(6) WITH TIME ZONE,ended_at TIMESTAMP(6) WITH TIME ZONE,start_revision BIGINT,end_revision BIGINT,summary CLOB,session_status VARCHAR,created_at TIMESTAMP(6) WITH TIME ZONE,updated_at TIMESTAMP(6) WITH TIME ZONE,last_activity_at TIMESTAMP(6) WITH TIME ZONE,close_reason VARCHAR,total_operations BIGINT,total_changes BIGINT,operation_counts_json CLOB)",
                "CREATE TABLE project_session_participants(id UUID PRIMARY KEY,session_id UUID,user_id UUID,display_name VARCHAR,operation_count BIGINT,change_count BIGINT)",
                "CREATE TABLE project_activities(id UUID PRIMARY KEY,project_id UUID,operation_id UUID,actor_user_id UUID,actor_display_name VARCHAR,revision_before BIGINT,revision_after BIGINT,operation_type VARCHAR,source VARCHAR,description CLOB,payload_json CLOB,session_id UUID,created_at TIMESTAMP(6) WITH TIME ZONE)",
                "CREATE TABLE project_ai_messages(message_sequence BIGINT PRIMARY KEY,message_id UUID,project_id UUID,request_id UUID,author_user_id UUID,author_display_name VARCHAR,message_role VARCHAR,message_type VARCHAR,content CLOB,project_revision BIGINT,created_at TIMESTAMP(6) WITH TIME ZONE,review_json CLOB,plan_json CLOB,generation_status VARCHAR,generation_token UUID,generation_updated_at TIMESTAMP(6) WITH TIME ZONE)"
            };
            try (var statement = sql.createStatement()) { for (String ddl : schema) statement.execute(ddl); }
            insert(sql, "app_users", owner, "Autor legado", email, hash, at); insert(sql, "app_users", editor, "Editor legado", editor + "@legacy.test", hash, at);
            insert(sql, "projects", project, owner, "Proyecto legado", 1L, docJson, at, at);
            insert(sql, "project_memberships", UUID.randomUUID(), project, editor, "EDITOR", at);
            insert(sql, "project_invitations", UUID.randomUUID(), project, "invitado@legacy.test", owner, "PENDING", at, null);
            insert(sql, "project_sessions", session, project, project, at, null, 0L, 1L, "Resumen persistido", "OPEN", at, at, at, null, 1L, 1L, "{\"CREATE_CLASS\":1}");
            insert(sql, "project_session_participants", UUID.randomUUID(), session, owner, "Autor legado", 1L, 1L);
            insert(sql, "project_activities", activity, project, UUID.randomUUID(), owner, "Autor legado", 0L, 1L, "CREATE_CLASS", "MANUAL", "creó la clase Cliente", payload, session, at);
            insert(sql, "project_ai_messages", 7L, message, project, request, owner, "Autor legado", "USER", "ASK", "¿Qué representa Cliente?", 1L, at, null, null, "COMPLETED", UUID.randomUUID(), at);
            insert(sql, "project_ai_messages", 8L, UUID.randomUUID(), project, request, null, "ClassForge AI", "ASSISTANT", "ASK", "Representa un cliente.", 1L, at, null, null, null, null, null);
        }
        return new Fixture(file, owner, editor, project, session, activity, message, document, email, hash);
    }
    void insert(Connection sql, String table, Object... values) throws Exception {
        String placeholders = String.join(",", Collections.nCopies(values.length, "?"));
        try (var statement = sql.prepareStatement("INSERT INTO " + table + " VALUES (" + placeholders + ")")) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i] instanceof Instant instant ? instant.atOffset(ZoneOffset.UTC) : values[i]);
            statement.executeUpdate();
        }
    }
}
