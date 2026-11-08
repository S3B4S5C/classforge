package com.classforge.project.persistence;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import java.util.*;
/** Document writes serialize project transactions through MongoDB write conflicts. */
public class ProjectWriteAccessImpl implements ProjectWriteAccess {
    private final MongoTemplate mongo;
    public ProjectWriteAccessImpl(MongoTemplate mongo) { this.mongo = mongo; }
    public Optional<ProjectEntity> acquireWrite(UUID id) {
        return Optional.ofNullable(mongo.findAndModify(Query.query(Criteria.where("id").is(id)),
                new Update().inc("_writeFence", 1), FindAndModifyOptions.options().returnNew(true), ProjectEntity.class));
    }
}
