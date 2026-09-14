package com.classforge.generation.spring.rendering;

import com.classforge.generation.spring.api.SpringApiDirectRelationModel;
import com.classforge.generation.spring.api.SpringApiEntityModel;
import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.api.SpringApiManyRelationModel;
import com.classforge.generation.spring.api.contract.SpringApiContract;
import com.classforge.generation.spring.api.contract.SpringApiContractOperation;
import com.classforge.generation.spring.api.contract.SpringApiContractParameter;
import com.classforge.generation.spring.api.contract.SpringApiContractPlanner;
import com.classforge.generation.spring.api.contract.SpringApiHttpMethod;
import com.classforge.generation.spring.api.contract.SpringApiParameterLocation;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnostic;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnosticCode;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringJavaType;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class SpringApiArtifactsRenderer {
    private static final String POSTMAN_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
    private final SpringApiContractPlanner contractPlanner = new SpringApiContractPlanner();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public List<GeneratedFile> render(SpringGenerationModel model, SpringApiGenerationPlan api) {
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(api, "api is required");
        if (!api.enabled()) return List.of();
        SpringApiContract contract = contractPlanner.plan(api);
        return List.of(
                textFile("openapi.yaml", renderOpenApi(model, api, contract)),
                textFile("postman_collection.json", renderPostman(model, api, contract))
        );
    }

    private GeneratedFile textFile(String path, String content) {
        return new GeneratedFile(
                path,
                GeneratedFileType.TEXT,
                SpringFreeMarkerRenderer.normalizeGeneratedText(content).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String renderOpenApi(SpringGenerationModel model, SpringApiGenerationPlan api, SpringApiContract contract) {
        StringBuilder out = new StringBuilder();
        line(out, 0, "openapi: 3.0.3");
        line(out, 0, "info:");
        line(out, 1, "title: " + quote(model.artifactName() + " API"));
        line(out, 1, "version: '1.0.0'");
        line(out, 1, "description: 'Generated deterministically by ClassForge from the CU-14 HTTP contract.'");
        line(out, 0, "servers:");
        line(out, 1, "- url: 'http://localhost:8080'");
        line(out, 0, "tags:");
        if (api.authEnabled()) {
            line(out, 1, "- name: 'Authentication'");
        }
        for (SpringApiEntityModel entity : api.entities()) {
            line(out, 1, "- name: " + quote(entity.className()));
        }
        if (contract.authEnabled()) {
            line(out, 0, "security:");
            line(out, 1, "- bearerAuth: []");
        }
        line(out, 0, "paths:");

        Map<String, List<SpringApiContractOperation>> byPath = contract.operations().stream()
                .collect(Collectors.groupingBy(
                        SpringApiContractOperation::path,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (Map.Entry<String, List<SpringApiContractOperation>> entry : byPath.entrySet()) {
            line(out, 1, quote(entry.getKey()) + ":");
            for (SpringApiContractOperation operation : entry.getValue()) {
                line(out, 2, operation.method().name().toLowerCase() + ":");
                line(out, 3, "tags:");
                line(out, 4, "- " + quote(operation.tag()));
                line(out, 3, "summary: " + quote(operation.summary()));
                line(out, 3, "operationId: " + operation.operationId());
                if (contract.authEnabled() && !operation.authenticationRequired()) {
                    line(out, 3, "security: []");
                }
                if (!operation.parameters().isEmpty()) {
                    line(out, 3, "parameters:");
                    for (SpringApiContractParameter parameter : operation.parameters()) {
                        line(out, 4, "- name: " + quote(parameter.name()));
                        line(out, 5, "in: " + parameter.location().name().toLowerCase());
                        line(out, 5, "required: " + parameter.required());
                        line(out, 5, "description: " + quote(parameter.description()));
                        line(out, 5, "schema:");
                        line(out, 6, "type: " + parameter.schemaType());
                        if (parameter.format() != null) line(out, 6, "format: " + parameter.format());
                        if ("direction".equals(parameter.name())) {
                            line(out, 6, "enum: [asc, desc]");
                            line(out, 6, "default: asc");
                        }
                        if ("page".equals(parameter.name())) {
                            line(out, 6, "minimum: 0");
                            line(out, 6, "default: 0");
                        }
                        if ("size".equals(parameter.name())) {
                            line(out, 6, "minimum: 1");
                            line(out, 6, "maximum: 200");
                            line(out, 6, "default: 20");
                        }
                        if (parameter.example() != null && !parameter.example().isBlank()) {
                            line(out, 5, "example: " + scalarExample(parameter.schemaType(), parameter.example()));
                        }
                    }
                }
                if (operation.requestSchema() != null) {
                    line(out, 3, "requestBody:");
                    line(out, 4, "required: true");
                    line(out, 4, "content:");
                    line(out, 5, "application/json:");
                    line(out, 6, "schema:");
                    line(out, 7, "$ref: '#/components/schemas/" + operation.requestSchema() + "'");
                }
                renderResponses(out, operation);
            }
        }

        line(out, 0, "components:");
        if (contract.authEnabled()) {
            line(out, 1, "securitySchemes:");
            line(out, 2, "bearerAuth:");
            line(out, 3, "type: http");
            line(out, 3, "scheme: bearer");
            line(out, 3, "bearerFormat: JWT");
        }
        line(out, 1, "schemas:");
        renderCommonSchemas(out, api);
        for (SpringApiEntityModel entity : api.entities()) {
            if (entity.id().kind() == SpringIdKind.COMPOSITE) {
                renderIdSchema(out, entity.className() + "Id", entity.id());
            }
        }
        for (SpringApiEntityModel entity : api.entities()) {
            renderEntityRequestSchema(out, entity, api);
            renderEntityResponseSchema(out, entity, api);
            renderPageSchema(out, entity);
        }
        return out.toString();
    }

    private void renderResponses(StringBuilder out, SpringApiContractOperation operation) {
        line(out, 3, "responses:");
        line(out, 4, quote(Integer.toString(operation.successStatus())) + ":");
        line(out, 5, "description: " + quote(successDescription(operation.successStatus())));
        if (operation.responseSchema() != null) {
            line(out, 5, "content:");
            line(out, 6, "application/json:");
            line(out, 7, "schema:");
            line(out, 8, "$ref: '#/components/schemas/" + operation.responseSchema() + "'");
        }
        errorResponse(out, 400, "Invalid request");
        if (operation.operationId().startsWith("get") || operation.operationId().startsWith("update")
                || operation.operationId().startsWith("delete")) {
            errorResponse(out, 404, "Resource not found");
        }
        if (operation.method() == SpringApiHttpMethod.POST || operation.method() == SpringApiHttpMethod.PUT
                || operation.method() == SpringApiHttpMethod.DELETE) {
            errorResponse(out, 409, "Data constraint conflict");
        }
    }

    private void errorResponse(StringBuilder out, int status, String description) {
        line(out, 4, quote(Integer.toString(status)) + ":");
        line(out, 5, "description: " + quote(description));
        line(out, 5, "content:");
        line(out, 6, "application/json:");
        line(out, 7, "schema:");
        line(out, 8, "$ref: '#/components/schemas/ErrorResponse'");
    }

    private String successDescription(int status) {
        return switch (status) {
            case 201 -> "Created";
            case 204 -> "Deleted";
            default -> "Successful response";
        };
    }

    private void renderCommonSchemas(StringBuilder out, SpringApiGenerationPlan api) {
        line(out, 2, "ErrorResponse:");
        line(out, 3, "type: object");
        line(out, 3, "required: [error, message]");
        line(out, 3, "properties:");
        stringProperty(out, 4, "error", false, false, "BAD_REQUEST");
        stringProperty(out, 4, "message", false, false, "Invalid request.");

        line(out, 2, "CountResponse:");
        line(out, 3, "type: object");
        line(out, 3, "required: [count]");
        line(out, 3, "properties:");
        line(out, 4, "count:");
        line(out, 5, "type: integer");
        line(out, 5, "format: int64");
        line(out, 5, "example: 1");

        if (api.authEnabled()) {
            line(out, 2, "LoginRequest:");
            line(out, 3, "type: object");
            line(out, 3, "required: [username, password]");
            line(out, 3, "properties:");
            stringProperty(out, 4, "username", false, false, "admin");
            stringProperty(out, 4, "password", true, false, "change-me");

            line(out, 2, "LoginResponse:");
            line(out, 3, "type: object");
            line(out, 3, "required: [accessToken, tokenType, expiresInSeconds]");
            line(out, 3, "properties:");
            stringProperty(out, 4, "accessToken", false, false, "eyJhbGciOiJIUzI1NiJ9...");
            stringProperty(out, 4, "tokenType", false, false, "Bearer");
            line(out, 4, "expiresInSeconds:");
            line(out, 5, "type: integer");
            line(out, 5, "format: int64");
            line(out, 5, "example: 3600");
        }
    }

    private void renderIdSchema(StringBuilder out, String schemaName, SpringEntityIdModel id) {
        line(out, 2, schemaName + ":");
        line(out, 3, "type: object");
        if (!id.fields().isEmpty()) {
            line(out, 3, "required: [" + id.fields().stream().map(f -> f.fieldName()).collect(Collectors.joining(", ")) + "]");
        }
        line(out, 3, "properties:");
        id.fields().forEach(field -> javaTypeProperty(out, 4, field.fieldName(), field.javaType(), false, false));
    }

    private void renderEntityRequestSchema(StringBuilder out, SpringApiEntityModel entity, SpringApiGenerationPlan api) {
        line(out, 2, entity.className() + "Request:");
        line(out, 3, "type: object");
        List<String> required = new ArrayList<>();
        entity.requestFields().stream()
                .filter(field -> !field.nullable() && !entity.isPasswordAttribute(field.sourceAttributeId()))
                .map(SpringScalarFieldModel::fieldName).forEach(required::add);
        entity.directRelations().stream().filter(relation -> !relation.optional())
                .map(relation -> relation.fieldName() + "Id").forEach(required::add);
        if (!required.isEmpty()) line(out, 3, "required: [" + String.join(", ", required) + "]");
        line(out, 3, "properties:");
        for (SpringScalarFieldModel field : entity.requestFields()) {
            javaTypeProperty(out, 4, field.fieldName(), field.javaType(),
                    entity.isPasswordAttribute(field.sourceAttributeId()), field.nullable());
            if (entity.isPasswordAttribute(field.sourceAttributeId())) {
                line(out, 5, "description: 'Required when creating an account; optional on update to preserve the existing password.'");
            }
        }
        for (SpringApiDirectRelationModel relation : entity.directRelations()) {
            relationIdProperty(out, 4, relation.fieldName() + "Id", relation.targetEntityClassName(), relation.targetId(), api, relation.optional());
        }
        for (SpringApiManyRelationModel relation : entity.manyToManyRelations()) {
            line(out, 4, relation.fieldName() + "Ids:");
            line(out, 5, "type: array");
            line(out, 5, "uniqueItems: true");
            line(out, 5, "items:");
            relationIdSchema(out, 6, relation.targetEntityClassName(), relation.targetId());
        }
    }

    private void renderEntityResponseSchema(StringBuilder out, SpringApiEntityModel entity, SpringApiGenerationPlan api) {
        line(out, 2, entity.className() + "Response:");
        line(out, 3, "type: object");
        List<String> required = new ArrayList<>();
        entity.responseFields().stream().filter(field -> !field.nullable()).map(SpringScalarFieldModel::fieldName).forEach(required::add);
        entity.directRelations().stream().filter(relation -> !relation.optional()).map(relation -> relation.fieldName() + "Id").forEach(required::add);
        if (!required.isEmpty()) line(out, 3, "required: [" + String.join(", ", required) + "]");
        line(out, 3, "properties:");
        for (SpringScalarFieldModel field : entity.responseFields()) {
            javaTypeProperty(out, 4, field.fieldName(), field.javaType(), false, field.nullable());
        }
        for (SpringApiDirectRelationModel relation : entity.directRelations()) {
            relationIdProperty(out, 4, relation.fieldName() + "Id", relation.targetEntityClassName(), relation.targetId(), api, relation.optional());
        }
        for (SpringApiManyRelationModel relation : entity.manyToManyRelations()) {
            line(out, 4, relation.fieldName() + "Ids:");
            line(out, 5, "type: array");
            line(out, 5, "uniqueItems: true");
            line(out, 5, "items:");
            relationIdSchema(out, 6, relation.targetEntityClassName(), relation.targetId());
        }
    }

    private void renderPageSchema(StringBuilder out, SpringApiEntityModel entity) {
        line(out, 2, entity.className() + "PageResponse:");
        line(out, 3, "type: object");
        line(out, 3, "required: [content, page, size, totalElements, totalPages]");
        line(out, 3, "properties:");
        line(out, 4, "content:");
        line(out, 5, "type: array");
        line(out, 5, "items:");
        line(out, 6, "$ref: '#/components/schemas/" + entity.className() + "Response'");
        integerProperty(out, 4, "page", "int32", "0");
        integerProperty(out, 4, "size", "int32", "20");
        integerProperty(out, 4, "totalElements", "int64", "1");
        integerProperty(out, 4, "totalPages", "int32", "1");
    }

    private void relationIdProperty(
            StringBuilder out,
            int indent,
            String name,
            String targetClass,
            SpringEntityIdModel id,
            SpringApiGenerationPlan api,
            boolean nullable
    ) {
        line(out, indent, name + ":");
        relationIdSchema(out, indent + 1, targetClass, id);
        if (nullable) line(out, indent + 1, "nullable: true");
    }

    private void relationIdSchema(StringBuilder out, int indent, String targetClass, SpringEntityIdModel id) {
        if (id.kind() == SpringIdKind.COMPOSITE) {
            line(out, indent, "$ref: '#/components/schemas/" + targetClass + "Id'");
        } else {
            javaTypeSchema(out, indent, id.javaType());
        }
    }

    private void javaTypeProperty(StringBuilder out, int indent, String name, SpringJavaType type, boolean writeOnly, boolean nullable) {
        line(out, indent, name + ":");
        javaTypeSchema(out, indent + 1, type);
        if (writeOnly) line(out, indent + 1, "writeOnly: true");
        if (nullable) line(out, indent + 1, "nullable: true");
        line(out, indent + 1, "example: " + scalarExample(schemaType(type), example(type)));
    }

    private void javaTypeSchema(StringBuilder out, int indent, SpringJavaType type) {
        line(out, indent, "type: " + schemaType(type));
        String format = format(type);
        if (format != null) line(out, indent, "format: " + format);
    }

    private void stringProperty(StringBuilder out, int indent, String name, boolean writeOnly, boolean nullable, String example) {
        line(out, indent, name + ":");
        line(out, indent + 1, "type: string");
        if (writeOnly) line(out, indent + 1, "writeOnly: true");
        if (nullable) line(out, indent + 1, "nullable: true");
        line(out, indent + 1, "example: " + quote(example));
    }

    private void integerProperty(StringBuilder out, int indent, String name, String format, String example) {
        line(out, indent, name + ":");
        line(out, indent + 1, "type: integer");
        line(out, indent + 1, "format: " + format);
        line(out, indent + 1, "example: " + example);
    }

    private String renderPostman(SpringGenerationModel model, SpringApiGenerationPlan api, SpringApiContract contract) {
        LinkedHashMap<String, Object> collection = new LinkedHashMap<>();
        collection.put("info", linked(
                "name", model.artifactName() + " API",
                "description", "Generated deterministically by ClassForge from the CU-14 HTTP contract.",
                "schema", POSTMAN_SCHEMA
        ));
        collection.put("variable", collectionVariables(api));
        if (api.authEnabled()) {
            collection.put("auth", bearerAuth());
        }

        List<Object> folders = new ArrayList<>();
        if (api.authEnabled()) {
            folders.add(folder("Authentication", contract.operations().stream()
                    .filter(operation -> "Authentication".equals(operation.tag())).toList(), api));
        }
        for (SpringApiEntityModel entity : api.entities()) {
            folders.add(folder(entity.className(), contract.operations().stream()
                    .filter(operation -> entity.className().equals(operation.tag())).toList(), api));
        }
        collection.put("item", folders);

        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(collection);
        } catch (JacksonException exception) {
            throw new GeneratedProjectException(List.of(new GeneratedProjectDiagnostic(
                    GeneratedProjectDiagnosticCode.TEMPLATE_RENDER_FAILED,
                    "postman_collection.json",
                    exception.getMessage()
            )), exception);
        }
    }

    private List<Object> collectionVariables(SpringApiGenerationPlan api) {
        List<Object> variables = new ArrayList<>();
        variables.add(variable("baseUrl", "http://localhost:8080"));
        variables.add(variable("q", ""));
        variables.add(variable("sort", ""));
        variables.add(variable("direction", "asc"));
        variables.add(variable("page", "0"));
        variables.add(variable("size", "20"));
        variables.add(variable("filterValue", ""));
        if (api.authEnabled()) {
            variables.add(variable("jwt", ""));
            variables.add(variable("username", "admin"));
            variables.add(variable("password", "change-me"));
        }
        for (SpringApiEntityModel entity : api.entities()) {
            if (entity.id().kind() == SpringIdKind.SIMPLE) {
                variables.add(variable(idVariable(entity.className(), "id"), example(entity.id().javaType())));
            } else {
                entity.id().fields().forEach(field -> variables.add(variable(
                        idVariable(entity.className(), field.fieldName()), example(field.javaType())
                )));
            }
        }
        return variables;
    }

    private Map<String, Object> variable(String key, String value) {
        return linked("key", key, "value", value, "type", "string");
    }

    private Map<String, Object> folder(String name, List<SpringApiContractOperation> operations, SpringApiGenerationPlan api) {
        return linked(
                "name", name,
                "item", operations.stream().map(operation -> requestItem(operation, api)).toList()
        );
    }

    private Map<String, Object> requestItem(SpringApiContractOperation operation, SpringApiGenerationPlan api) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("method", operation.method().name());
        if (operation.requestSchema() != null) {
            request.put("header", List.of(linked("key", "Content-Type", "value", "application/json", "type", "text")));
            request.put("body", linked(
                    "mode", "raw",
                    "raw", json(sampleBody(operation.requestSchema(), api)),
                    "options", linked("raw", linked("language", "json"))
            ));
        }
        if (api.authEnabled() && !operation.authenticationRequired()) {
            request.put("auth", linked("type", "noauth"));
        }
        request.put("url", postmanUrl(operation));
        request.put("description", operation.summary());

        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("name", operation.operationId());
        item.put("request", request);
        if ("bootstrapAuthentication".equals(operation.operationId()) || "loginAuthentication".equals(operation.operationId())) {
            item.put("event", List.of(linked(
                    "listen", "test",
                    "script", linked(
                            "type", "text/javascript",
                            "exec", List.of(
                                    "if (pm.response.code >= 200 && pm.response.code < 300) {",
                                    "  const body = pm.response.json();",
                                    "  if (body.accessToken) pm.collectionVariables.set('jwt', body.accessToken);",
                                    "}"
                            )
                    )
            )));
        }
        return item;
    }

    private Map<String, Object> postmanUrl(SpringApiContractOperation operation) {
        List<Object> query = new ArrayList<>();
        String rawPath = operation.path();
        List<String> pathParts = new ArrayList<>();
        for (String segment : operation.path().substring(1).split("/")) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String parameter = segment.substring(1, segment.length() - 1);
                String variable = idVariable(operation.tag(), parameter);
                pathParts.add("{{" + variable + "}}");
                rawPath = rawPath.replace(segment, "{{" + variable + "}}");
            } else {
                pathParts.add(segment);
            }
        }

        List<String> enabledQuery = new ArrayList<>();
        for (SpringApiContractParameter parameter : operation.parameters()) {
            if (parameter.location() != SpringApiParameterLocation.QUERY) continue;
            String value;
            boolean disabled;
            if (parameter.required()) {
                value = "{{" + idVariable(operation.tag(), parameter.name()) + "}}";
                disabled = false;
            } else if ("page".equals(parameter.name()) || "size".equals(parameter.name())) {
                value = "{{" + parameter.name() + "}}";
                disabled = false;
            } else if (parameter.name().startsWith("filter.")) {
                value = "{{filterValue}}";
                disabled = true;
            } else {
                value = "{{" + parameter.name() + "}}";
                disabled = true;
            }
            LinkedHashMap<String, Object> queryEntry = linked("key", parameter.name(), "value", value);
            if (disabled) queryEntry.put("disabled", true);
            query.add(queryEntry);
            if (!disabled) enabledQuery.add(parameter.name() + "=" + value);
        }
        String raw = "{{baseUrl}}" + rawPath;
        if (!enabledQuery.isEmpty()) raw += "?" + String.join("&", enabledQuery);
        LinkedHashMap<String, Object> url = linked(
                "raw", raw,
                "host", List.of("{{baseUrl}}"),
                "path", pathParts
        );
        if (!query.isEmpty()) url.put("query", query);
        return url;
    }

    private Map<String, Object> sampleBody(String schema, SpringApiGenerationPlan api) {
        if ("LoginRequest".equals(schema)) {
            return linked("username", "{{username}}", "password", "{{password}}");
        }
        SpringApiEntityModel entity = api.entities().stream()
                .filter(candidate -> (candidate.className() + "Request").equals(schema))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown generated request schema: " + schema));
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        for (SpringScalarFieldModel field : entity.requestFields()) {
            if (entity.isUsernameAttribute(field.sourceAttributeId())) body.put(field.fieldName(), "{{username}}");
            else if (entity.isPasswordAttribute(field.sourceAttributeId())) body.put(field.fieldName(), "{{password}}");
            else body.put(field.fieldName(), sampleValue(field.javaType()));
        }
        for (SpringApiDirectRelationModel relation : entity.directRelations()) {
            body.put(relation.fieldName() + "Id", sampleId(relation.targetId()));
        }
        for (SpringApiManyRelationModel relation : entity.manyToManyRelations()) {
            body.put(relation.fieldName() + "Ids", List.of(sampleId(relation.targetId())));
        }
        return body;
    }

    private Object sampleId(SpringEntityIdModel id) {
        if (id.kind() == SpringIdKind.SIMPLE) return sampleValue(id.javaType());
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        id.fields().forEach(field -> value.put(field.fieldName(), sampleValue(field.javaType())));
        return value;
    }

    private Object sampleValue(SpringJavaType type) {
        return switch (type) {
            case STRING -> "example";
            case INTEGER -> 1;
            case LONG -> 1L;
            case BIG_DECIMAL -> 10.50;
            case BOOLEAN -> true;
            case LOCAL_DATE -> "2026-09-13";
            case LOCAL_DATE_TIME -> "2026-09-13T12:00:00";
            case UUID -> "11111111-1111-1111-1111-111111111111";
        };
    }

    private String json(Object value) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not render Postman request example", exception);
        }
    }

    private Map<String, Object> bearerAuth() {
        return linked(
                "type", "bearer",
                "bearer", List.of(linked("key", "token", "value", "{{jwt}}", "type", "string"))
        );
    }

    @SuppressWarnings("unchecked")
    private <K, V> LinkedHashMap<K, V> linked(Object... values) {
        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((K) values[i], (V) values[i + 1]);
        }
        return result;
    }

    private String idVariable(String tag, String name) {
        String prefix = tag == null || tag.isBlank()
                ? "resource"
                : tag.substring(0, 1).toLowerCase() + tag.substring(1);
        return prefix + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String schemaType(SpringJavaType type) {
        return switch (type) {
            case INTEGER, LONG -> "integer";
            case BIG_DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private String format(SpringJavaType type) {
        return switch (type) {
            case INTEGER -> "int32";
            case LONG -> "int64";
            case BIG_DECIMAL -> "double";
            case LOCAL_DATE -> "date";
            case LOCAL_DATE_TIME -> "date-time";
            case UUID -> "uuid";
            default -> null;
        };
    }

    private String example(SpringJavaType type) {
        return switch (type) {
            case STRING -> "example";
            case INTEGER, LONG -> "1";
            case BIG_DECIMAL -> "10.50";
            case BOOLEAN -> "true";
            case LOCAL_DATE -> "2026-09-13";
            case LOCAL_DATE_TIME -> "2026-09-13T12:00:00";
            case UUID -> "11111111-1111-1111-1111-111111111111";
        };
    }

    private String scalarExample(String schemaType, String example) {
        if ("integer".equals(schemaType) || "number".equals(schemaType) || "boolean".equals(schemaType)) return example;
        return quote(example);
    }

    private String quote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
    }

    private void line(StringBuilder out, int indent, String value) {
        out.append("  ".repeat(Math.max(0, indent))).append(value).append('\n');
    }
}
