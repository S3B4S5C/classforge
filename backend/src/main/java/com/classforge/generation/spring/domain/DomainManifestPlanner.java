package com.classforge.generation.spring.domain;

import com.classforge.generation.spring.api.SpringApiDirectRelationModel;
import com.classforge.generation.spring.api.SpringApiEntityModel;
import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.api.SpringApiManyRelationModel;
import com.classforge.generation.spring.api.contract.SpringApiContract;
import com.classforge.generation.spring.api.contract.SpringApiContractOperation;
import com.classforge.generation.spring.application.SpringBootGenerationMode;
import com.classforge.generation.spring.model.SpringDirectRelationModel;
import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringEntityModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringInheritanceKind;
import com.classforge.generation.spring.model.SpringJavaType;
import com.classforge.generation.spring.model.SpringManyToManyRelationModel;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DomainManifestPlanner {
    public DomainManifestPlan plan(
            SpringGenerationModel model,
            SpringApiGenerationPlan api,
            SpringApiContract contract
    ) {
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(api, "api is required");
        Objects.requireNonNull(contract, "contract is required");
        if (!api.enabled()) {
            throw new IllegalArgumentException("Domain Manifest requires an enabled Spring API generation plan.");
        }
        if (contract.mode() != api.mode()) {
            throw new IllegalArgumentException("Spring API plan and contract generation modes must match.");
        }

        Map<UUID, SpringEntityModel> modelById = new HashMap<>();
        Map<String, SpringEntityModel> modelByClass = new HashMap<>();
        for (SpringEntityModel entity : model.entities()) {
            modelById.put(entity.sourceClassId(), entity);
            modelByClass.put(entity.className(), entity);
        }
        Map<String, SpringApiEntityModel> apiByClass = new HashMap<>();
        for (SpringApiEntityModel entity : api.entities()) {
            apiByClass.put(entity.className(), entity);
        }

        List<DomainManifestPlan.Entity> entities = api.entities().stream()
                .sorted(Comparator.comparing(SpringApiEntityModel::className))
                .map(entity -> entity(modelById, modelByClass, apiByClass, entity, contract))
                .toList();

        List<DomainManifestPlan.Operation> operations = contract.operations().stream()
                .map(operation -> operation(operation, apiByClass, api))
                .toList();

        return new DomainManifestPlan(
                DomainManifestPlan.SCHEMA_VERSION,
                api.mode().name(),
                new DomainManifestPlan.Api(
                        "http://localhost:8080",
                        "openapi.yaml",
                        "postman_collection.json"
                ),
                authentication(api),
                entities,
                operations
        );
    }

    private DomainManifestPlan.Entity entity(
            Map<UUID, SpringEntityModel> modelById,
            Map<String, SpringEntityModel> modelByClass,
            Map<String, SpringApiEntityModel> apiByClass,
            SpringApiEntityModel apiEntity,
            SpringApiContract contract
    ) {
        SpringEntityModel source = Objects.requireNonNull(
                modelById.get(apiEntity.sourceClassId()),
                "API entity source class must exist in Spring generation model: " + apiEntity.sourceClassId()
        );

        List<DomainManifestPlan.Attribute> attributes = apiEntity.scalarFields().stream()
                .sorted(Comparator.comparing(SpringScalarFieldModel::fieldName))
                .map(field -> attribute(apiEntity, field))
                .toList();

        List<DomainManifestPlan.Relation> relations = new ArrayList<>();
        for (SpringApiDirectRelationModel relation : apiEntity.directRelations()) {
            SpringDirectRelationModel sourceRelation = findDirectRelation(source, modelByClass, relation);
            SpringEntityModel target = requireTarget(modelByClass, relation.targetEntityClassName());
            relations.add(new DomainManifestPlan.Relation(
                    sourceRelation.sourceRelationshipId(),
                    sourceRelation.umlType().name(),
                    sourceRelation.kind().name(),
                    relation.fieldName(),
                    target.sourceClassId(),
                    target.logicalName(),
                    relation.optional(),
                    relation.fieldName() + "Id",
                    identifier(relation.targetId()),
                    sourceRelation.onDeleteCascade()
            ));
        }
        for (SpringApiManyRelationModel relation : apiEntity.manyToManyRelations()) {
            SpringManyToManyRelationModel sourceRelation = findManyRelation(source, modelByClass, relation);
            SpringEntityModel target = requireTarget(modelByClass, relation.targetEntityClassName());
            relations.add(new DomainManifestPlan.Relation(
                    sourceRelation.sourceRelationshipId(),
                    sourceRelation.umlType().name(),
                    "MANY_TO_MANY",
                    relation.fieldName(),
                    target.sourceClassId(),
                    target.logicalName(),
                    null,
                    relation.fieldName() + "Ids",
                    identifier(relation.targetId()),
                    null
            ));
        }
        relations.sort(Comparator.comparing(DomainManifestPlan.Relation::name)
                .thenComparing(relation -> relation.id().toString()));

        List<SpringApiContractOperation> entityOperations = contract.operations().stream()
                .filter(operation -> apiEntity.className().equals(operation.tag()))
                .toList();
        List<String> operationIds = entityOperations.stream().map(SpringApiContractOperation::operationId).toList();
        List<String> capabilities = entityOperations.stream()
                .map(operation -> capability(operation.operationId()))
                .distinct()
                .toList();

        return new DomainManifestPlan.Entity(
                source.sourceClassId(),
                source.logicalName(),
                source.className(),
                source.tableName(),
                "/api/" + source.tableName(),
                source.logicalName(),
                List.of(),
                identifier(apiEntity.id()),
                inheritance(source, modelByClass),
                attributes,
                relations,
                capabilities,
                operationIds
        );
    }

    private DomainManifestPlan.Attribute attribute(SpringApiEntityModel entity, SpringScalarFieldModel field) {
        boolean password = entity.isPasswordAttribute(field.sourceAttributeId());
        boolean username = entity.isUsernameAttribute(field.sourceAttributeId());
        boolean readable = !password;
        boolean identifier = field.identifier();
        boolean automaticallyGeneratedIdentifier = identifier
                && entity.id().automaticallyGenerated()
                && !username
                && !password;
        return new DomainManifestPlan.Attribute(
                field.sourceAttributeId(),
                field.logicalName(),
                field.fieldName(),
                field.columnName(),
                semanticType(field.javaType()),
                field.nullable(),
                identifier,
                identifier,
                readable,
                !automaticallyGeneratedIdentifier,
                !identifier,
                readable,
                readable,
                readable,
                password,
                password,
                new DomainManifestPlan.Validation(
                        !automaticallyGeneratedIdentifier && (password || username || !field.nullable()),
                        username || (!identifier && !password && !field.nullable()),
                        username
                )
        );
    }

    private DomainManifestPlan.Identifier identifier(SpringEntityIdModel id) {
        return new DomainManifestPlan.Identifier(
                id.kind().name(),
                id.fields().stream()
                        .map(field -> new DomainManifestPlan.IdentifierField(
                                field.sourceAttributeId(), field.fieldName(), semanticType(field.javaType())
                        ))
                        .toList()
        );
    }

    private DomainManifestPlan.Inheritance inheritance(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> modelByClass
    ) {
        UUID superId = null;
        String superName = null;
        if (entity.inheritance().kind() == SpringInheritanceKind.JOINED_SUBCLASS) {
            SpringEntityModel parent = Objects.requireNonNull(
                    modelByClass.get(entity.inheritance().superClassName()),
                    "JOINED superclass must resolve: " + entity.inheritance().superClassName()
            );
            superId = parent.sourceClassId();
            superName = parent.logicalName();
        }
        return new DomainManifestPlan.Inheritance(
                entity.inheritance().kind().name(),
                superId,
                superName,
                entity.inheritance().primaryKeyJoinColumns().stream()
                        .map(column -> new DomainManifestPlan.JoinColumn(
                                column.localColumnName(), column.referencedColumnName(), column.nullable()
                        ))
                        .toList()
        );
    }

    private DomainManifestPlan.Operation operation(
            SpringApiContractOperation operation,
            Map<String, SpringApiEntityModel> apiByClass,
            SpringApiGenerationPlan api
    ) {
        SpringApiEntityModel entity = apiByClass.get(operation.tag());
        if (entity == null && "Authentication".equals(operation.tag())) {
            entity = api.authEntity();
        }
        return new DomainManifestPlan.Operation(
                operation.operationId(),
                capability(operation.operationId()),
                entity == null ? null : entity.sourceClassId(),
                operation.method().name(),
                operation.path(),
                operation.authenticationRequired(),
                operation.requestSchema(),
                operation.responseSchema()
        );
    }

    private DomainManifestPlan.Authentication authentication(SpringApiGenerationPlan api) {
        if (api.mode() == SpringBootGenerationMode.SIMPLE_CRUD) {
            return new DomainManifestPlan.Authentication(false, null, null, null, null, null, null, null, null);
        }
        SpringApiEntityModel auth = Objects.requireNonNull(api.authEntity(), "Auth mode requires an auth entity.");
        return new DomainManifestPlan.Authentication(
                true,
                "BEARER_JWT",
                auth.sourceClassId(),
                auth.usernameAttributeId(),
                auth.passwordAttributeId(),
                "jwt",
                3600,
                "bootstrapAuthentication",
                "loginAuthentication"
        );
    }

    private SpringEntityModel requireTarget(Map<String, SpringEntityModel> modelByClass, String className) {
        return Objects.requireNonNull(modelByClass.get(className), "Relation target must resolve: " + className);
    }

    private SpringDirectRelationModel findDirectRelation(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> modelByClass,
            SpringApiDirectRelationModel apiRelation
    ) {
        List<SpringDirectRelationModel> candidates = new ArrayList<>();
        collectDirectRelations(entity, modelByClass, candidates);
        return candidates.stream()
                .filter(relation -> relation.fieldName().equals(apiRelation.fieldName()))
                .filter(relation -> relation.targetEntityClassName().equals(apiRelation.targetEntityClassName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot resolve source direct relation: " + apiRelation.fieldName()));
    }

    private void collectDirectRelations(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> modelByClass,
            List<SpringDirectRelationModel> result
    ) {
        if (entity.inheritance().superClassName() != null) {
            SpringEntityModel parent = modelByClass.get(entity.inheritance().superClassName());
            if (parent != null) collectDirectRelations(parent, modelByClass, result);
        }
        result.addAll(entity.directRelations());
    }

    private SpringManyToManyRelationModel findManyRelation(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> modelByClass,
            SpringApiManyRelationModel apiRelation
    ) {
        List<SpringManyToManyRelationModel> candidates = new ArrayList<>();
        collectManyRelations(entity, modelByClass, candidates);
        return candidates.stream()
                .filter(relation -> relation.fieldName().equals(apiRelation.fieldName()))
                .filter(relation -> relation.targetEntityClassName().equals(apiRelation.targetEntityClassName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot resolve source many-to-many relation: " + apiRelation.fieldName()));
    }

    private void collectManyRelations(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> modelByClass,
            List<SpringManyToManyRelationModel> result
    ) {
        if (entity.inheritance().superClassName() != null) {
            SpringEntityModel parent = modelByClass.get(entity.inheritance().superClassName());
            if (parent != null) collectManyRelations(parent, modelByClass, result);
        }
        result.addAll(entity.manyToManyRelations());
    }

    private String capability(String operationId) {
        if (operationId.equals("bootstrapAuthentication")) return "AUTH_BOOTSTRAP";
        if (operationId.equals("loginAuthentication")) return "AUTH_LOGIN";
        if (operationId.startsWith("list")) return "LIST";
        if (operationId.startsWith("count")) return "COUNT";
        if (operationId.startsWith("create")) return "CREATE";
        if (operationId.startsWith("get")) return "GET";
        if (operationId.startsWith("update")) return "UPDATE";
        if (operationId.startsWith("delete")) return "DELETE";
        throw new IllegalArgumentException("Unsupported canonical operationId: " + operationId);
    }

    private DomainManifestPlan.SemanticType semanticType(SpringJavaType type) {
        return switch (type) {
            case STRING -> DomainManifestPlan.SemanticType.STRING;
            case INTEGER -> DomainManifestPlan.SemanticType.INTEGER;
            case LONG -> DomainManifestPlan.SemanticType.LONG;
            case BIG_DECIMAL -> DomainManifestPlan.SemanticType.DECIMAL;
            case BOOLEAN -> DomainManifestPlan.SemanticType.BOOLEAN;
            case LOCAL_DATE -> DomainManifestPlan.SemanticType.DATE;
            case LOCAL_DATE_TIME -> DomainManifestPlan.SemanticType.DATETIME;
            case UUID -> DomainManifestPlan.SemanticType.UUID;
        };
    }
}
