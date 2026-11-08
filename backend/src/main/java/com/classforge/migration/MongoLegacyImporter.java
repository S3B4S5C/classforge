package com.classforge.migration;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class MongoLegacyImporter {
    private final MongoTemplate mongo;
    public MongoLegacyImporter(MongoTemplate mongo) { this.mongo = mongo; }
    public record Report(String sourceSha256, boolean alreadyImported, Map<String,Long> verifiedCounts) { }

    @Transactional
    public Report importSnapshot(String digest, Map<String,List<Document>> snapshot) {
        var marker = mongo.findById(digest, Document.class, "legacy_migrations");
        if (marker != null) {
            Map<String,Long> prior = new TreeMap<>();
            marker.get("counts", Document.class).forEach((collection, count) -> prior.put(collection, ((Number) count).longValue()));
            return new Report(digest, true, prior);
        }
        Map<String,Long> verified = new TreeMap<>();
        long maxSequence = 0;
        for (var collection : snapshot.entrySet()) {
            for (var document : collection.getValue()) {
                Object id = document.get("_id");
                if (id == null) throw new IllegalStateException("Un registro no tiene identificador: " + collection.getKey());
                var query = Query.query(Criteria.where("_id").is(id));
                if (mongo.exists(query, collection.getKey())) throw new IllegalStateException("El destino ya contiene el ID " + id + " en " + collection.getKey() + "; no se sobrescribió ningún dato.");
                mongo.insert(document, collection.getKey());
                var read = mongo.findOne(query, Document.class, collection.getKey());
                if (read == null || !normalize(document).equals(normalize(read))) throw new IllegalStateException("La verificación del registro falló: " + collection.getKey() + "/" + id);
                if (collection.getKey().equals("project_ai_messages")) maxSequence = Math.max(maxSequence, document.get("sequence", Number.class).longValue());
            }
            verified.put(collection.getKey(), (long) collection.getValue().size());
        }
        mongo.upsert(Query.query(Criteria.where("_id").is("project_ai_messages")), new Update().max("value", maxSequence), "_sequences");
        mongo.insert(new Document("_id", digest).append("counts", verified).append("completedAt", Instant.now().toString()), "legacy_migrations");
        return new Report(digest, false, verified);
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> normalized = new TreeMap<>();
            map.forEach((key, item) -> normalized.put(key.toString(), normalize(item))); return normalized;
        }
        if (value instanceof List<?> list) return list.stream().map(this::normalize).toList();
        if (value instanceof org.bson.types.Binary binary && binary.getType() == 4) {
            var bytes = java.nio.ByteBuffer.wrap(binary.getData());
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        }
        if (value instanceof UUID uuid) return uuid.toString();
        if (value instanceof Number number) return new java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        return value;
    }
}
