package com.classforge.assistant;

import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlRelationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LlamaLanguageModelGateway
        implements LanguageModelGateway {

    static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(90);

    static final int MAX_COMPLETION_TOKENS =
            256;

    static final int MAX_FOCUSED_CLASSES =
            8;

    static final int MAX_CLASS_CATALOG =
            32;

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9]+");

    private static final Pattern CAMEL_CASE =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String url;
    private final String model;

    public LlamaLanguageModelGateway(
            JsonMapper jsonMapper,
            @Value("${classforge.assistant.llama-url:http://127.0.0.1:8092}")
            String url,
            @Value("${classforge.assistant.llama-model:local-model}")
            String model
    ) {
        this.jsonMapper =
                jsonMapper;

        this.url =
                url.endsWith("/")
                        ? url.substring(
                                0,
                                url.length() - 1
                        )
                        : url;

        this.model =
                model;

        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(3)
                        )
                        .build();
    }

    @Override
    public AssistantSemanticPlan plan(
            String userText,
            ProjectDocument document
    ) {
        try {
            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "model",
                    model
            );

            request.put(
                    "temperature",
                    0.0
            );

            request.put(
                    "max_tokens",
                    MAX_COMPLETION_TOKENS
            );

            request.put(
                    "stream",
                    false
            );

            request.put(
                    "messages",
                    List.of(
                            Map.of(
                                    "role",
                                    "system",
                                    "content",
                                    systemPrompt()
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    userPrompt(
                                            userText,
                                            document
                                    )
                            )
                    )
            );

            request.put(
                    "response_format",
                    Map.of(
                            "type",
                            "json_schema",
                            "json_schema",
                            Map.of(
                                    "name",
                                    "classforge_assistant_intent",
                                    "strict",
                                    true,
                                    "schema",
                                    schema()
                            )
                    )
            );

            HttpRequest httpRequest =
                    HttpRequest
                            .newBuilder(
                                    URI.create(
                                            url
                                                    + "/v1/chat/completions"
                                    )
                            )
                            .timeout(
                                    REQUEST_TIMEOUT
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            jsonMapper.writeValueAsString(
                                                    request
                                            )
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                throw new AssistantPlanningException(
                        "llama.cpp respondio HTTP "
                                + response.statusCode()
                );
            }

            JsonNode root =
                    jsonMapper.readTree(
                            response.body()
                    );

            JsonNode choices =
                    root.get(
                            "choices"
                    );

            if (
                    choices == null
                            || choices.isEmpty()
            ) {
                throw new AssistantPlanningException(
                        "llama.cpp no devolvio choices"
                );
            }

            JsonNode firstChoice =
                    choices.get(0);

            JsonNode finishReason =
                    firstChoice.get(
                            "finish_reason"
                    );

            if (
                    finishReason != null
                            && "length".equalsIgnoreCase(
                                    finishReason.asString()
                            )
            ) {
                throw new AssistantPlanningException(
                        "El plan excedio el limite local de "
                                + MAX_COMPLETION_TOKENS
                                + " tokens. Simplifica la instruccion."
                );
            }

            JsonNode message =
                    firstChoice.get(
                            "message"
                    );

            JsonNode content =
                    message == null
                            ? null
                            : message.get(
                            "content"
                    );

            if (
                    content == null
                            || content.asString()
                                    .isBlank()
            ) {
                throw new AssistantPlanningException(
                        "llama.cpp devolvio una respuesta vacia"
                );
            }

            return jsonMapper.readValue(
                    content.asString(),
                    AssistantSemanticPlan.class
            );
        } catch (
                HttpTimeoutException exception
        ) {
            throw new AssistantPlanningException(
                    "El modelo local supero "
                            + REQUEST_TIMEOUT.toSeconds()
                            + " segundos preparando el plan. "
                            + "No se aplico ningun cambio.",
                    exception
            );
        } catch (
                AssistantPlanningException exception
        ) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException(
                    "No pudimos comunicarnos con llama.cpp en "
                            + url,
                    exception
            );
        }
    }

    private String systemPrompt() {
        return """
                Eres el interprete UML de ClassForge.
                Devuelve solo la intencion semantica solicitada usando el JSON Schema.

                REGLAS:
                - Java resuelve UUIDs, revision, layout, validacion y Command Bus.
                - CREATE_CLASS contiene sus atributos en attributes[].
                - No recrees clases que ya aparecen en existingClassNames.
                - Una orden de relacion no autoriza cambios de atributos.
                - Omite propiedades que no apliquen; no rellenes null innecesarios.
                - Atributo sin tipo suficiente: STRING + typeSource DEFAULT.
                - Tipo dicho por el usuario: typeSource EXPLICIT.
                - Tipo deducido claramente: typeSource INFERRED.
                - id: identifier=true y nullable=false salvo instruccion explicita.
                - Defaults: visibility PRIVATE, nullable true.
                - Tipos: STRING INTEGER LONG DECIMAL BOOLEAN DATE DATETIME UUID CUSTOM.
                - Relaciones: ASSOCIATION AGGREGATION COMPOSITION GENERALIZATION.
                - Aggregation/composition: source=whole.
                - Generalization: source=subclass, target=superclass, sin multiplicidades.
                - upper infinito = -1.
                - Si no se especifica tipo de relacion, ASSOCIATION.
                - Si no se especifica multiplicidad, 1..1 en ambos extremos.
                - No inventes clases, atributos ni acciones no solicitadas.

                EJEMPLOS:
                "Crea Veterinario y ponle id UUID y nombre"
                -> CREATE_CLASS Veterinario con id UUID EXPLICIT y nombre STRING DEFAULT.

                "A Veterinario agregale telefono y correo"
                -> ADD_ATTRIBUTES Veterinario: telefono STRING DEFAULT, correo STRING DEFAULT.

                "Conecta Animal con Veterinario"
                -> CREATE_RELATIONSHIP Animal -> Veterinario, ASSOCIATION, 1..1 a 1..1.

                "Animal esta compuesto por Mascota"
                -> CREATE_RELATIONSHIP Animal -> Mascota, COMPOSITION.
                """;
    }

    private String userPrompt(
            String userText,
            ProjectDocument document
    ) throws Exception {
        return """
                CONTEXTO:
                %s

                PETICION:
                %s
                """
                .formatted(
                        compactContext(
                                userText,
                                document
                        ),
                        userText
                );
    }

    String compactContext(
            String userText,
            ProjectDocument document
    ) throws Exception {
        List<UmlClass> allClasses =
                document.umlModel()
                        .classes();

        List<UmlRelationship> allRelationships =
                document.umlModel()
                        .relationships();

        Map<UUID, UmlClass> classesById =
                allClasses.stream()
                        .collect(
                                Collectors.toMap(
                                        UmlClass::id,
                                        umlClass ->
                                                umlClass
                                )
                        );

        LinkedHashSet<UUID> directIds =
                allClasses.stream()
                        .filter(
                                umlClass ->
                                        mentionsEntity(
                                                userText,
                                                umlClass.name()
                                        )
                        )
                        .map(
                                UmlClass::id
                        )
                        .collect(
                                Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        );

        LinkedHashSet<UUID> focusedIds =
                new LinkedHashSet<>(
                        directIds
                );

        if (
                !directIds.isEmpty()
                        && focusedIds.size()
                        < MAX_FOCUSED_CLASSES
        ) {
            for (
                    UmlRelationship relationship
                    : allRelationships
            ) {
                if (
                        focusedIds.size()
                                >= MAX_FOCUSED_CLASSES
                ) {
                    break;
                }

                if (
                        directIds.contains(
                                relationship.sourceClassId()
                        )
                ) {
                    focusedIds.add(
                            relationship.targetClassId()
                    );
                }

                if (
                        focusedIds.size()
                                >= MAX_FOCUSED_CLASSES
                ) {
                    break;
                }

                if (
                        directIds.contains(
                                relationship.targetClassId()
                        )
                ) {
                    focusedIds.add(
                            relationship.sourceClassId()
                    );
                }
            }
        }

        List<Map<String, Object>> focusedClasses =
                focusedIds.stream()
                        .limit(
                                MAX_FOCUSED_CLASSES
                        )
                        .map(
                                classesById::get
                        )
                        .filter(
                                java.util.Objects::nonNull
                        )
                        .map(
                                this::focusedClass
                        )
                        .toList();

        Set<UUID> focusedSet =
                new LinkedHashSet<>(
                        focusedIds.stream()
                                .limit(
                                        MAX_FOCUSED_CLASSES
                                )
                                .toList()
                );

        List<Map<String, Object>> relationships =
                allRelationships.stream()
                        .filter(
                                relationship ->
                                        focusedSet.contains(
                                                relationship.sourceClassId()
                                        )
                                                || focusedSet.contains(
                                                relationship.targetClassId()
                                        )
                        )
                        .limit(16)
                        .map(
                                relationship ->
                                        compactRelationship(
                                                relationship,
                                                classesById
                                        )
                        )
                        .toList();

        LinkedHashSet<String> catalog =
                new LinkedHashSet<>();

        focusedClasses.forEach(
                item ->
                        catalog.add(
                                String.valueOf(
                                        item.get(
                                                "name"
                                        )
                                )
                        )
        );

        allClasses.stream()
                .map(
                        UmlClass::name
                )
                .limit(
                        MAX_CLASS_CATALOG
                )
                .forEach(
                        catalog::add
                );

        Map<String, Object> context =
                new LinkedHashMap<>();

        context.put(
                "classCount",
                allClasses.size()
        );

        context.put(
                "existingClassNames",
                catalog.stream()
                        .limit(
                                MAX_CLASS_CATALOG
                        )
                        .toList()
        );

        if (!focusedClasses.isEmpty()) {
            context.put(
                    "focusedClasses",
                    focusedClasses
            );
        }

        if (!relationships.isEmpty()) {
            context.put(
                    "relationships",
                    relationships
            );
        }

        return jsonMapper.writeValueAsString(
                context
        );
    }

    private Map<String, Object> focusedClass(
            UmlClass umlClass
    ) {
        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "name",
                umlClass.name()
        );

        if (!umlClass.attributes().isEmpty()) {
            result.put(
                    "attributes",
                    umlClass.attributes()
                            .stream()
                            .map(
                                    attribute -> {
                                        Map<String, Object> item =
                                                new LinkedHashMap<>();

                                        item.put(
                                                "name",
                                                attribute.name()
                                        );

                                        item.put(
                                                "type",
                                                attribute.dataType()
                                                        .name()
                                        );

                                        if (attribute.identifier()) {
                                            item.put(
                                                    "id",
                                                    true
                                            );
                                        }

                                        if (!attribute.nullable()) {
                                            item.put(
                                                    "required",
                                                    true
                                            );
                                        }

                                        return item;
                                    }
                            )
                            .toList()
            );
        }

        return result;
    }

    private Map<String, Object> compactRelationship(
            UmlRelationship relationship,
            Map<UUID, UmlClass> classesById
    ) {
        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "source",
                className(
                        relationship.sourceClassId(),
                        classesById
                )
        );

        result.put(
                "target",
                className(
                        relationship.targetClassId(),
                        classesById
                )
        );

        result.put(
                "type",
                relationship.type()
                        .name()
        );

        if (
                relationship.sourceMultiplicity()
                        != null
        ) {
            result.put(
                    "sourceMultiplicity",
                    multiplicity(
                            relationship.sourceMultiplicity()
                    )
            );
        }

        if (
                relationship.targetMultiplicity()
                        != null
        ) {
            result.put(
                    "targetMultiplicity",
                    multiplicity(
                            relationship.targetMultiplicity()
                    )
            );
        }

        return result;
    }

    private String className(
            UUID id,
            Map<UUID, UmlClass> classesById
    ) {
        UmlClass umlClass =
                classesById.get(
                        id
                );

        return umlClass == null
                ? "?"
                : umlClass.name();
    }

    private String multiplicity(
            Multiplicity value
    ) {
        return value.lower()
                + ".."
                + (
                value.upper() == null
                        ? "*"
                        : value.upper()
        );
    }

    boolean mentionsEntity(
            String userText,
            String entity
    ) {
        if (
                userText == null
                        || userText.isBlank()
                        || entity == null
                        || entity.isBlank()
        ) {
            return false;
        }

        String normalizedText =
                normalize(
                        userText
                );

        String normalizedEntity =
                normalize(
                        splitCamelCase(
                                entity
                        )
                );

        String paddedText =
                " "
                        + normalizedText
                        + " ";

        String paddedEntity =
                " "
                        + normalizedEntity
                        + " ";

        if (
                paddedText.contains(
                        paddedEntity
                )
        ) {
            return true;
        }

        List<String> textTokens =
                tokens(
                        normalizedText
                );

        return tokens(
                normalizedEntity
        )
                .stream()
                .allMatch(
                        entityToken ->
                                textTokens.stream()
                                        .anyMatch(
                                                textToken ->
                                                        sameOrPlural(
                                                                entityToken,
                                                                textToken
                                                        )
                                        )
                );
    }

    private boolean sameOrPlural(
            String entityToken,
            String textToken
    ) {
        if (
                entityToken.equals(
                        textToken
                )
        ) {
            return true;
        }

        if (
                textToken.equals(
                        entityToken
                                + "s"
                )
                        || textToken.equals(
                        entityToken
                                + "es"
                )
        ) {
            return true;
        }

        return entityToken.endsWith("z")
                && entityToken.length() > 1
                && textToken.equals(
                entityToken.substring(
                        0,
                        entityToken.length() - 1
                )
                        + "ces"
        );
    }

    private String splitCamelCase(
            String value
    ) {
        return CAMEL_CASE.matcher(
                        value
                )
                .replaceAll(
                        " "
                );
    }

    private String normalize(
            String value
    ) {
        String decomposed =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                DIACRITICS.matcher(
                                decomposed
                        )
                        .replaceAll(
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        return NON_WORD.matcher(
                        withoutMarks
                )
                .replaceAll(
                        " "
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private List<String> tokens(
            String value
    ) {
        if (value.isBlank()) {
            return List.of();
        }

        return Arrays.asList(
                value.split(
                        " "
                )
        );
    }

    Map<String, Object> schema() {
        Map<String, Object> attributeProperties =
                new LinkedHashMap<>();

        attributeProperties.put(
                "name",
                nullable(
                        "string"
                )
        );

        attributeProperties.put(
                "dataType",
                nullableEnum(
                        List.of(
                                "STRING",
                                "INTEGER",
                                "LONG",
                                "DECIMAL",
                                "BOOLEAN",
                                "DATE",
                                "DATETIME",
                                "UUID",
                                "CUSTOM"
                        )
                )
        );

        attributeProperties.put(
                "customTypeName",
                nullable(
                        "string"
                )
        );

        attributeProperties.put(
                "visibility",
                nullableEnum(
                        List.of(
                                "PUBLIC",
                                "PRIVATE",
                                "PROTECTED",
                                "PACKAGE"
                        )
                )
        );

        attributeProperties.put(
                "nullable",
                nullable(
                        "boolean"
                )
        );

        attributeProperties.put(
                "identifier",
                nullable(
                        "boolean"
                )
        );

        attributeProperties.put(
                "typeSource",
                nullableEnum(
                        List.of(
                                "EXPLICIT",
                                "INFERRED",
                                "DEFAULT"
                        )
                )
        );

        Map<String, Object> attributeSchema =
                new LinkedHashMap<>();

        attributeSchema.put(
                "type",
                "object"
        );

        attributeSchema.put(
                "additionalProperties",
                false
        );

        attributeSchema.put(
                "properties",
                attributeProperties
        );

        attributeSchema.put(
                "required",
                List.of(
                        "name"
                )
        );

        Map<String, Object> actionProperties =
                new LinkedHashMap<>();

        actionProperties.put(
                "type",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of(
                                "CREATE_CLASS",
                                "RENAME_CLASS",
                                "DELETE_CLASS",
                                "ADD_ATTRIBUTES",
                                "UPDATE_ATTRIBUTE",
                                "DELETE_ATTRIBUTE",
                                "CREATE_RELATIONSHIP",
                                "UPDATE_RELATIONSHIP",
                                "DELETE_RELATIONSHIP"
                        )
                )
        );

        actionProperties.put(
                "className",
                nullable(
                        "string"
                )
        );

        actionProperties.put(
                "newName",
                nullable(
                        "string"
                )
        );

        actionProperties.put(
                "attributes",
                Map.of(
                        "type",
                        "array",
                        "maxItems",
                        30,
                        "items",
                        attributeSchema
                )
        );

        for (
                String name
                : List.of(
                        "attributeName",
                        "newAttributeName",
                        "customTypeName",
                        "sourceClassName",
                        "targetClassName"
                )
        ) {
            actionProperties.put(
                    name,
                    nullable(
                            "string"
                    )
            );
        }

        actionProperties.put(
                "dataType",
                nullableEnum(
                        List.of(
                                "STRING",
                                "INTEGER",
                                "LONG",
                                "DECIMAL",
                                "BOOLEAN",
                                "DATE",
                                "DATETIME",
                                "UUID",
                                "CUSTOM"
                        )
                )
        );

        actionProperties.put(
                "visibility",
                nullableEnum(
                        List.of(
                                "PUBLIC",
                                "PRIVATE",
                                "PROTECTED",
                                "PACKAGE"
                        )
                )
        );

        actionProperties.put(
                "nullable",
                nullable(
                        "boolean"
                )
        );

        actionProperties.put(
                "identifier",
                nullable(
                        "boolean"
                )
        );

        actionProperties.put(
                "relationshipType",
                nullableEnum(
                        List.of(
                                "ASSOCIATION",
                                "AGGREGATION",
                                "COMPOSITION",
                                "GENERALIZATION"
                        )
                )
        );

        for (
                String name
                : List.of(
                        "sourceLower",
                        "sourceUpper",
                        "targetLower",
                        "targetUpper"
                )
        ) {
            actionProperties.put(
                    name,
                    nullable(
                            "integer"
                    )
            );
        }

        Map<String, Object> actionSchema =
                new LinkedHashMap<>();

        actionSchema.put(
                "type",
                "object"
        );

        actionSchema.put(
                "additionalProperties",
                false
        );

        actionSchema.put(
                "properties",
                actionProperties
        );

        actionSchema.put(
                "required",
                List.of(
                        "type"
                )
        );

        Map<String, Object> root =
                new LinkedHashMap<>();

        root.put(
                "type",
                "object"
        );

        root.put(
                "additionalProperties",
                false
        );

        root.put(
                "properties",
                Map.of(
                        "summary",
                        Map.of(
                                "type",
                                "string"
                        ),
                        "actions",
                        Map.of(
                                "type",
                                "array",
                                "minItems",
                                1,
                                "maxItems",
                                30,
                                "items",
                                actionSchema
                        )
                )
        );

        root.put(
                "required",
                List.of(
                        "summary",
                        "actions"
                )
        );

        return root;
    }

    private Map<String, Object> nullable(
            String type
    ) {
        return Map.of(
                "type",
                List.of(
                        type,
                        "null"
                )
        );
    }

    private Map<String, Object> nullableEnum(
            List<String> values
    ) {
        return Map.of(
                "anyOf",
                List.of(
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                values
                        ),
                        Map.of(
                                "type",
                                "null"
                        )
                )
        );
    }
}