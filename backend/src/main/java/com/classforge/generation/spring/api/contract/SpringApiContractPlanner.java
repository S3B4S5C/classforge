package com.classforge.generation.spring.api.contract;

import com.classforge.generation.spring.api.SpringApiEntityModel;
import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.model.SpringIdFieldModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringJavaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SpringApiContractPlanner {
    public SpringApiContract plan(SpringApiGenerationPlan api) {
        Objects.requireNonNull(api, "api is required");
        if (!api.enabled()) {
            return new SpringApiContract(null, List.of());
        }

        List<SpringApiContractOperation> operations = new ArrayList<>();
        if (api.authEnabled()) {
            SpringApiEntityModel auth = Objects.requireNonNull(api.authEntity(), "auth entity is required");
            operations.add(new SpringApiContractOperation(
                    SpringApiHttpMethod.POST,
                    "/api/auth/bootstrap",
                    "bootstrapAuthentication",
                    "Authentication",
                    "Create the first authentication account and issue a JWT",
                    false,
                    List.of(),
                    auth.className() + "Request",
                    "LoginResponse",
                    200
            ));
            operations.add(new SpringApiContractOperation(
                    SpringApiHttpMethod.POST,
                    "/api/auth/login",
                    "loginAuthentication",
                    "Authentication",
                    "Authenticate and issue a JWT",
                    false,
                    List.of(),
                    "LoginRequest",
                    "LoginResponse",
                    200
            ));
        }

        for (SpringApiEntityModel entity : api.entities()) {
            String basePath = "/api/" + entity.tableName();
            boolean secured = api.authEnabled();
            operations.add(new SpringApiContractOperation(
                    SpringApiHttpMethod.GET,
                    basePath,
                    "list" + entity.className(),
                    entity.className(),
                    "List " + entity.className() + " resources",
                    secured,
                    listParameters(entity),
                    null,
                    entity.className() + "PageResponse",
                    200
            ));
            operations.add(new SpringApiContractOperation(
                    SpringApiHttpMethod.GET,
                    basePath + "/count",
                    "count" + entity.className(),
                    entity.className(),
                    "Count " + entity.className() + " resources",
                    secured,
                    List.of(),
                    null,
                    "CountResponse",
                    200
            ));
            operations.add(new SpringApiContractOperation(
                    SpringApiHttpMethod.POST,
                    basePath,
                    "create" + entity.className(),
                    entity.className(),
                    "Create a " + entity.className() + " resource",
                    secured,
                    List.of(),
                    entity.className() + "Request",
                    entity.className() + "Response",
                    201
            ));

            if (entity.id().kind() == SpringIdKind.SIMPLE) {
                SpringIdFieldModel id = entity.id().fields().getFirst();
                List<SpringApiContractParameter> idParameter = List.of(parameter(
                        "id", SpringApiParameterLocation.PATH, true, id.javaType(), example(id.javaType()),
                        "Resource identifier"
                ));
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.GET,
                        basePath + "/{id}",
                        "get" + entity.className(),
                        entity.className(),
                        "Get a " + entity.className() + " resource",
                        secured,
                        idParameter,
                        null,
                        entity.className() + "Response",
                        200
                ));
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.PUT,
                        basePath + "/{id}",
                        "update" + entity.className(),
                        entity.className(),
                        "Update a " + entity.className() + " resource",
                        secured,
                        idParameter,
                        entity.className() + "Request",
                        entity.className() + "Response",
                        200
                ));
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.DELETE,
                        basePath + "/{id}",
                        "delete" + entity.className(),
                        entity.className(),
                        "Delete a " + entity.className() + " resource",
                        secured,
                        idParameter,
                        null,
                        null,
                        204
                ));
            } else {
                List<SpringApiContractParameter> idParameters = entity.id().fields().stream()
                        .map(field -> parameter(
                                field.fieldName(), SpringApiParameterLocation.QUERY, true,
                                field.javaType(), example(field.javaType()), "Composite identifier field"
                        ))
                        .toList();
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.GET,
                        basePath + "/by-id",
                        "get" + entity.className(),
                        entity.className(),
                        "Get a " + entity.className() + " resource by composite identifier",
                        secured,
                        idParameters,
                        null,
                        entity.className() + "Response",
                        200
                ));
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.PUT,
                        basePath + "/by-id",
                        "update" + entity.className(),
                        entity.className(),
                        "Update a " + entity.className() + " resource by composite identifier",
                        secured,
                        idParameters,
                        entity.className() + "Request",
                        entity.className() + "Response",
                        200
                ));
                operations.add(new SpringApiContractOperation(
                        SpringApiHttpMethod.DELETE,
                        basePath + "/by-id",
                        "delete" + entity.className(),
                        entity.className(),
                        "Delete a " + entity.className() + " resource by composite identifier",
                        secured,
                        idParameters,
                        null,
                        null,
                        204
                ));
            }
        }
        return new SpringApiContract(api.mode(), operations);
    }

    private List<SpringApiContractParameter> listParameters(SpringApiEntityModel entity) {
        List<SpringApiContractParameter> parameters = new ArrayList<>();
        parameters.add(new SpringApiContractParameter("q", SpringApiParameterLocation.QUERY, false,
                "string", null, "search", "Case-insensitive free-text search across response fields"));
        parameters.add(new SpringApiContractParameter("sort", SpringApiParameterLocation.QUERY, false,
                "string", null, firstSortableField(entity), "Response field used for sorting"));
        parameters.add(new SpringApiContractParameter("direction", SpringApiParameterLocation.QUERY, false,
                "string", null, "asc", "Sort direction: asc or desc"));
        parameters.add(new SpringApiContractParameter("page", SpringApiParameterLocation.QUERY, false,
                "integer", "int32", "0", "Zero-based page index"));
        parameters.add(new SpringApiContractParameter("size", SpringApiParameterLocation.QUERY, false,
                "integer", "int32", "20", "Page size, clamped to 1..200"));

        entity.responseFields().forEach(field -> parameters.add(new SpringApiContractParameter(
                "filter." + field.fieldName(), SpringApiParameterLocation.QUERY, false,
                "string", null, example(field.javaType()), "Case-insensitive contains filter"
        )));
        entity.directRelations().forEach(relation -> parameters.add(new SpringApiContractParameter(
                "filter." + relation.fieldName() + "Id", SpringApiParameterLocation.QUERY, false,
                "string", null, relation.targetId().fields().isEmpty() ? "id" : example(relation.targetId().fields().getFirst().javaType()),
                "Case-insensitive relation identifier filter"
        )));
        entity.manyToManyRelations().forEach(relation -> parameters.add(new SpringApiContractParameter(
                "filter." + relation.fieldName() + "Ids", SpringApiParameterLocation.QUERY, false,
                "string", null, "id", "Case-insensitive relation identifier-set filter"
        )));
        return List.copyOf(parameters);
    }

    private String firstSortableField(SpringApiEntityModel entity) {
        if (!entity.responseFields().isEmpty()) return entity.responseFields().getFirst().fieldName();
        if (!entity.directRelations().isEmpty()) return entity.directRelations().getFirst().fieldName() + "Id";
        if (!entity.manyToManyRelations().isEmpty()) return entity.manyToManyRelations().getFirst().fieldName() + "Ids";
        return "";
    }

    private SpringApiContractParameter parameter(
            String name,
            SpringApiParameterLocation location,
            boolean required,
            SpringJavaType javaType,
            String example,
            String description
    ) {
        return new SpringApiContractParameter(
                name, location, required, schemaType(javaType), format(javaType), example, description
        );
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
            case INTEGER -> "1";
            case LONG -> "1";
            case BIG_DECIMAL -> "10.50";
            case BOOLEAN -> "true";
            case LOCAL_DATE -> "2026-09-13";
            case LOCAL_DATE_TIME -> "2026-09-13T12:00:00";
            case UUID -> "11111111-1111-1111-1111-111111111111";
        };
    }
}
