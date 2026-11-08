package com.classforge.projectassistant.persistence;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import java.util.*;
public class ProjectAiRequestWriteAccessImpl implements ProjectAiRequestWriteAccess {
    private final MongoTemplate mongo;
    public ProjectAiRequestWriteAccessImpl(MongoTemplate mongo) { this.mongo = mongo; }
    public Optional<ProjectAiMessageEntity> lockRequest(UUID project, UUID request) {
        return Optional.ofNullable(mongo.findAndModify(Query.query(Criteria.where("projectId").is(project)
                        .and("requestId").is(request).and("role").is("USER")), new Update().inc("_writeFence", 1),
                FindAndModifyOptions.options().returnNew(true), ProjectAiMessageEntity.class));
    }
}
