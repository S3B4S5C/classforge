package com.classforge.migration;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.project.persistence.*;
import com.classforge.projectassistant.persistence.ProjectAiMessageEntity;
import com.classforge.activity.ProjectActivityEntity;
import com.classforge.session.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** Offline adapter only. H2 is excluded from the application runtime and loaded by the migration task. */
@Service
public class LegacyH2MigrationService {
    public static final Map<String,Class<?>> TABLES = Map.ofEntries(
            Map.entry("APP_USERS", UserEntity.class), Map.entry("PROJECTS", ProjectEntity.class),
            Map.entry("PROJECT_MEMBERSHIPS", ProjectMembershipEntity.class), Map.entry("PROJECT_INVITATIONS", ProjectInvitationEntity.class),
            Map.entry("PROJECT_AI_MESSAGES", ProjectAiMessageEntity.class), Map.entry("PROJECT_ACTIVITIES", ProjectActivityEntity.class),
            Map.entry("PROJECT_SESSIONS", ProjectSessionEntity.class), Map.entry("PROJECT_SESSION_PARTICIPANTS", ProjectSessionParticipantEntity.class));
    private final MongoTemplate mongo;
    private final ProjectMapper mapper;
    private final MongoLegacyImporter importer;
    public LegacyH2MigrationService(MongoTemplate mongo, ProjectMapper mapper, MongoLegacyImporter importer) {
        this.mongo = mongo; this.mapper = mapper; this.importer = importer;
    }

    public MongoLegacyImporter.Report migrate(Path source) throws Exception {
        Path file = source.toAbsolutePath().normalize();
        if (!file.toString().endsWith(".mv.db") || !Files.isRegularFile(file)) throw new IllegalArgumentException("Indica un archivo H2 .mv.db existente.");
        byte[] bytes = Files.readAllBytes(file);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path backup = file.resolveSibling(file.getFileName() + ".before-mongo.bak");
        if (!Files.exists(backup)) Files.copy(file, backup);
        String base = file.toString().substring(0, file.toString().length() - ".mv.db".length()).replace('\\', '/');
        Map<String,List<Document>> documents = new TreeMap<>();
        Class.forName("org.h2.Driver");
        try (var sql = DriverManager.getConnection("jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE", "sa", "")) {
            for (var entry : TABLES.entrySet()) {
                String table = entry.getKey();
                try (var found = sql.getMetaData().getTables(null, "PUBLIC", table, new String[]{"BASE TABLE"})) {
                    if (!found.next()) continue;
                }
                List<Document> rows = new ArrayList<>();
                try (var statement = sql.createStatement(); var result = statement.executeQuery("SELECT * FROM " + table)) {
                    while (result.next()) rows.add(canonical(result, entry.getValue()));
                }
                documents.put(mongo.getCollectionName(entry.getValue()), rows);
            }
        }
        if (!Arrays.equals(bytes, Files.readAllBytes(file))) throw new IllegalStateException("El archivo fuente cambió durante la lectura; detén el backend anterior y repite la migración.");
        return importer.importSnapshot(digest, documents);
    }

    private Document canonical(ResultSet row, Class<?> type) throws SQLException {
        var raw = new Document(); var metadata = row.getMetaData();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            String sqlName = metadata.getColumnName(column).toLowerCase(Locale.ROOT);
            Object value = row.getObject(column);
            if (value == null) continue;
            if (value instanceof Clob) value = row.getString(column);
            if (value instanceof OffsetDateTime at) value = at.toInstant();
            if (value instanceof Timestamp at) value = at.toInstant();
            if (value instanceof Instant at) value = new DateTimeFormatterBuilder().appendInstant(9).toFormatter().format(at);
            String field = switch (sqlName) {
                case "id", "message_id" -> "_id";
                case "message_sequence" -> "sequence";
                case "message_role" -> "role";
                case "message_type" -> "type";
                case "session_status" -> "status";
                case "operation_count" -> "operations";
                case "change_count" -> "changes";
                case "uml_model" -> "documentJson";
                default -> camel(sqlName);
            };
            raw.put(field, value);
        }
        Object entity = mongo.getConverter().read(type, raw);
        if (entity instanceof ProjectEntity project) entity = mapper.toEntity(mapper.toDomain(project));
        Document canonical = new Document(); mongo.getConverter().write(entity, canonical);
        return canonical;
    }

    private String camel(String value) {
        var parts = value.split("_"); var result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return result.toString();
    }
}
