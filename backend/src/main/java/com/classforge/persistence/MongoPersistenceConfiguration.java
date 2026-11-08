package com.classforge.persistence;

import com.classforge.projectassistant.persistence.ProjectAiMessageEntity;
import com.mongodb.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

@Configuration
public class MongoPersistenceConfiguration {
    private static final DateTimeFormatter INSTANT = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        // Fixed-width UTC strings preserve nanoseconds and sortable chronology; BSON Date truncates milliseconds.
        return MongoCustomConversions.create(config -> config.registerConverter(new InstantWriter()).registerConverter(new InstantReader()));
    }
    @WritingConverter static class InstantWriter implements Converter<Instant,String> { public String convert(Instant value) { return INSTANT.format(value); } }
    @ReadingConverter static class InstantReader implements Converter<String,Instant> { public Instant convert(String value) { return Instant.parse(value); } }

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        var options = TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();
        return new MongoTransactionManager(factory, options) {
            @Override protected void doCommit(MongoTransactionObject transaction) throws Exception {
                for (int attempt = 0;; attempt++) {
                    try { super.doCommit(transaction); return; }
                    catch (MongoException failure) {
                        if (!failure.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL) || attempt >= 5) throw failure;
                    }
                }
            }
        };
    }

    @Bean
    public SmartInitializingSingleton mongoSchema(MongoTemplate mongo) {
        return () -> {
            var hello = mongo.executeCommand(new org.bson.Document("hello", 1));
            if (!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) {
                throw new IllegalStateException("ClassForge necesita MongoDB replica set para conservar transacciones. Ejecuta docker compose -f compose.mongo.yml up -d --wait.");
            }
            for (String collection : List.of("app_users", "projects", "project_memberships", "project_invitations",
                    "project_ai_messages", "project_activities", "project_sessions", "project_session_participants",
                    "project_user_visits", "project_notifications", "_sequences", "legacy_migrations")) {
                if (!mongo.collectionExists(collection)) mongo.createCollection(collection);
            }
            mongo.indexOps("app_users").createIndex(new Index().on("email", Sort.Direction.ASC).unique()
                    .collation(Collation.of("en").strength(2)).named("uk_user_email"));
            index(mongo, "projects", false, "ownerId", "updatedAt");
            index(mongo, "project_memberships", true, "projectId", "userId");
            index(mongo, "project_memberships", false, "userId");
            index(mongo, "project_invitations", false, "invitedEmail", "status", "createdAt");
            index(mongo, "project_invitations", false, "projectId", "status");
            index(mongo, "project_ai_messages", true, "projectId", "requestId", "role");
            index(mongo, "project_ai_messages", true, "sequence");
            index(mongo, "project_ai_messages", false, "projectId", "sequence");
            index(mongo, "project_activities", true, "projectId", "operationId");
            index(mongo, "project_activities", true, "projectId", "revisionAfter");
            index(mongo, "project_activities", false, "projectId", "createdAt");
            index(mongo, "project_activities", false, "projectId", "actorUserId", "createdAt");
            index(mongo, "project_activities", false, "sessionId", "revisionAfter");
            index(mongo, "project_sessions", false, "projectId", "startRevision");
            index(mongo, "project_sessions", false, "status", "lastActivityAt");
            mongo.indexOps("project_sessions").createIndex(new Index().on("projectId", Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status").is("OPEN"))).named("uk_open_session_project"));
            index(mongo, "project_session_participants", true, "sessionId", "userId");
            index(mongo, "project_user_visits", true, "projectId", "userId");
            index(mongo, "project_notifications", true, "dedupKey");
            index(mongo, "project_notifications", false, "recipientUserId", "read", "createdAt");
        };
    }

    private void index(MongoTemplate mongo, String collection, boolean unique, String... fields) {
        var index = new Index().named((unique ? "uk_" : "idx_") + String.join("_", fields));
        for (String field : fields) index.on(field, Sort.Direction.ASC);
        if (unique) index.unique();
        mongo.indexOps(collection).createIndex(index);
    }

    @Bean
    public BeforeConvertCallback<ProjectAiMessageEntity> messageSequence(org.springframework.beans.factory.ObjectProvider<MongoTemplate> templates) {
        return (entity, collection) -> {
            if (entity.getSequence() == null) {
                MongoTemplate mongo = templates.getObject();
                var counter = mongo.findAndModify(Query.query(Criteria.where("id").is("project_ai_messages")),
                        new Update().inc("value", 1), FindAndModifyOptions.options().upsert(true).returnNew(true), Sequence.class);
                entity.assignSequence(counter.value);
            }
            return entity;
        };
    }
    @Document("_sequences") public static class Sequence {
        @org.springframework.data.annotation.Id public String id;
        public long value;
    }
}
