package com.classforge.generation.spring.domain;

import java.util.List;
import java.util.UUID;

public record DomainManifestPlan(
        String schemaVersion,
        String generationMode,
        Api api,
        Authentication authentication,
        List<Entity> entities,
        List<Operation> operations
) {
    public static final String SCHEMA_VERSION = "1.0";

    public DomainManifestPlan {
        entities = List.copyOf(entities == null ? List.of() : entities);
        operations = List.copyOf(operations == null ? List.of() : operations);
    }

    public record Api(String baseUrl, String openApiFile, String postmanFile) { }

    public record Authentication(
            boolean enabled,
            String scheme,
            UUID entityId,
            UUID usernameAttributeId,
            UUID passwordAttributeId,
            String tokenVariable,
            Integer expiresInSeconds,
            String bootstrapOperationId,
            String loginOperationId
    ) { }

    public record Entity(
            UUID id,
            String logicalName,
            String codeName,
            String tableName,
            String endpoint,
            String displayName,
            List<String> aliases,
            Identifier identifier,
            Inheritance inheritance,
            List<Attribute> attributes,
            List<Relation> relations,
            List<String> capabilities,
            List<String> operationIds
    ) {
        public Entity {
            aliases = List.copyOf(aliases == null ? List.of() : aliases);
            attributes = List.copyOf(attributes == null ? List.of() : attributes);
            relations = List.copyOf(relations == null ? List.of() : relations);
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            operationIds = List.copyOf(operationIds == null ? List.of() : operationIds);
        }
    }

    public record Identifier(String kind, List<IdentifierField> fields) {
        public Identifier { fields = List.copyOf(fields == null ? List.of() : fields); }
    }

    public record IdentifierField(UUID attributeId, String name, SemanticType type) { }

    public record Inheritance(
            String kind,
            UUID superEntityId,
            String superEntityName,
            List<JoinColumn> primaryKeyJoinColumns
    ) {
        public Inheritance {
            primaryKeyJoinColumns = List.copyOf(primaryKeyJoinColumns == null ? List.of() : primaryKeyJoinColumns);
        }
    }

    public record JoinColumn(String localColumnName, String referencedColumnName, boolean nullable) { }

    public record Attribute(
            UUID id,
            String logicalName,
            String apiName,
            String columnName,
            SemanticType type,
            boolean nullable,
            boolean identifier,
            boolean immutable,
            boolean readable,
            boolean createWritable,
            boolean updateWritable,
            boolean searchable,
            boolean filterable,
            boolean sortable,
            boolean sensitive,
            boolean writeOnly,
            Validation validation
    ) { }

    public record Validation(boolean requiredOnCreate, boolean requiredOnUpdate, boolean unique) { }

    public record Relation(
            UUID id,
            String umlType,
            String kind,
            String name,
            UUID targetEntityId,
            String targetEntityName,
            Boolean optional,
            String requestField,
            Identifier targetIdentifier,
            Boolean onDeleteCascade
    ) { }

    public record Operation(
            String operationId,
            String capability,
            UUID entityId,
            String method,
            String path,
            boolean authenticationRequired,
            String requestSchema,
            String responseSchema
    ) { }

    public enum SemanticType {
        STRING, INTEGER, LONG, DECIMAL, BOOLEAN, DATE, DATETIME, UUID
    }
}
