package com.classforge.generation.spring.rendering;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnostic;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnosticCode;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class DomainManifestRenderer {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public GeneratedFile render(DomainManifestPlan plan) {
        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toMap(plan));
            return new GeneratedFile(
                    "domain-manifest.json",
                    GeneratedFileType.TEXT,
                    SpringFreeMarkerRenderer.normalizeGeneratedText(json).getBytes(StandardCharsets.UTF_8)
            );
        } catch (JacksonException exception) {
            throw new GeneratedProjectException(List.of(new GeneratedProjectDiagnostic(
                    GeneratedProjectDiagnosticCode.TEMPLATE_RENDER_FAILED,
                    "domain-manifest.json",
                    exception.getMessage()
            )), exception);
        }
    }

    private Map<String, Object> toMap(DomainManifestPlan plan) {
        LinkedHashMap<String, Object> out = linked();
        out.put("schemaVersion", plan.schemaVersion());
        out.put("generationMode", plan.generationMode());
        out.put("api", linked(
                "baseUrl", plan.api().baseUrl(),
                "openApiFile", plan.api().openApiFile(),
                "postmanFile", plan.api().postmanFile()
        ));
        out.put("authentication", authentication(plan.authentication()));
        out.put("entities", plan.entities().stream().map(this::entity).toList());
        out.put("operations", plan.operations().stream().map(this::operation).toList());
        return out;
    }

    private Map<String, Object> authentication(DomainManifestPlan.Authentication auth) {
        LinkedHashMap<String, Object> out = linked("enabled", auth.enabled());
        if (!auth.enabled()) return out;
        out.put("scheme", auth.scheme());
        out.put("entityId", auth.entityId().toString());
        out.put("usernameAttributeId", auth.usernameAttributeId().toString());
        out.put("passwordAttributeId", auth.passwordAttributeId().toString());
        out.put("tokenVariable", auth.tokenVariable());
        out.put("expiresInSeconds", auth.expiresInSeconds());
        out.put("bootstrapOperationId", auth.bootstrapOperationId());
        out.put("loginOperationId", auth.loginOperationId());
        return out;
    }

    private Map<String, Object> entity(DomainManifestPlan.Entity entity) {
        LinkedHashMap<String, Object> out = linked();
        out.put("id", entity.id().toString());
        out.put("logicalName", entity.logicalName());
        out.put("codeName", entity.codeName());
        out.put("tableName", entity.tableName());
        out.put("endpoint", entity.endpoint());
        out.put("displayName", entity.displayName());
        out.put("aliases", entity.aliases());
        out.put("identifier", identifier(entity.identifier()));
        out.put("inheritance", inheritance(entity.inheritance()));
        out.put("attributes", entity.attributes().stream().map(this::attribute).toList());
        out.put("relations", entity.relations().stream().map(this::relation).toList());
        out.put("capabilities", entity.capabilities());
        out.put("operationIds", entity.operationIds());
        return out;
    }

    private Map<String, Object> identifier(DomainManifestPlan.Identifier identifier) {
        return linked(
                "kind", identifier.kind(),
                "fields", identifier.fields().stream().map(field -> linked(
                        "attributeId", field.attributeId().toString(),
                        "name", field.name(),
                        "type", field.type().name()
                )).toList()
        );
    }

    private Map<String, Object> inheritance(DomainManifestPlan.Inheritance inheritance) {
        LinkedHashMap<String, Object> out = linked("kind", inheritance.kind());
        if (inheritance.superEntityId() != null) {
            out.put("superEntityId", inheritance.superEntityId().toString());
            out.put("superEntityName", inheritance.superEntityName());
        }
        if (!inheritance.primaryKeyJoinColumns().isEmpty()) {
            out.put("primaryKeyJoinColumns", inheritance.primaryKeyJoinColumns().stream().map(column -> linked(
                    "localColumnName", column.localColumnName(),
                    "referencedColumnName", column.referencedColumnName(),
                    "nullable", column.nullable()
            )).toList());
        } else {
            out.put("primaryKeyJoinColumns", List.of());
        }
        return out;
    }

    private Map<String, Object> attribute(DomainManifestPlan.Attribute attribute) {
        return linked(
                "id", attribute.id().toString(),
                "logicalName", attribute.logicalName(),
                "apiName", attribute.apiName(),
                "columnName", attribute.columnName(),
                "type", attribute.type().name(),
                "nullable", attribute.nullable(),
                "identifier", attribute.identifier(),
                "immutable", attribute.immutable(),
                "readable", attribute.readable(),
                "createWritable", attribute.createWritable(),
                "updateWritable", attribute.updateWritable(),
                "searchable", attribute.searchable(),
                "filterable", attribute.filterable(),
                "sortable", attribute.sortable(),
                "sensitive", attribute.sensitive(),
                "writeOnly", attribute.writeOnly(),
                "validation", linked(
                        "requiredOnCreate", attribute.validation().requiredOnCreate(),
                        "requiredOnUpdate", attribute.validation().requiredOnUpdate(),
                        "unique", attribute.validation().unique()
                )
        );
    }

    private Map<String, Object> relation(DomainManifestPlan.Relation relation) {
        LinkedHashMap<String, Object> out = linked(
                "id", relation.id().toString(),
                "umlType", relation.umlType(),
                "kind", relation.kind(),
                "name", relation.name(),
                "targetEntityId", relation.targetEntityId().toString(),
                "targetEntityName", relation.targetEntityName(),
                "requestField", relation.requestField(),
                "targetIdentifier", identifier(relation.targetIdentifier())
        );
        if (relation.optional() != null) out.put("optional", relation.optional());
        if (relation.onDeleteCascade() != null) out.put("onDeleteCascade", relation.onDeleteCascade());
        return out;
    }

    private Map<String, Object> operation(DomainManifestPlan.Operation operation) {
        LinkedHashMap<String, Object> out = linked(
                "operationId", operation.operationId(),
                "capability", operation.capability()
        );
        if (operation.entityId() != null) out.put("entityId", operation.entityId().toString());
        out.put("method", operation.method());
        out.put("path", operation.path());
        out.put("authenticationRequired", operation.authenticationRequired());
        if (operation.requestSchema() != null) out.put("requestSchema", operation.requestSchema());
        if (operation.responseSchema() != null) out.put("responseSchema", operation.responseSchema());
        return out;
    }

    private LinkedHashMap<String, Object> linked(Object... values) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (values.length % 2 != 0) throw new IllegalArgumentException("linked map requires key/value pairs");
        for (int i = 0; i < values.length; i += 2) out.put((String) values[i], values[i + 1]);
        return out;
    }
}
