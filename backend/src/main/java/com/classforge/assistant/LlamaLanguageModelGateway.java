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
import java.util.Optional;
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
    private final AssistantEntityReferenceResolver entityResolver =
            new AssistantEntityReferenceResolver();

    private final AssistantIntentHintResolver intentHintResolver =
            new AssistantIntentHintResolver();
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
        Optional<AssistantIntentHintResolver.IntentHint> intentHint =
                intentHintResolver.resolve(userText, document);

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
                                    systemPrompt(intentHint)
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    userPrompt(
                                            userText,
                                            document,
                                            intentHint
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
                                    schema(
                                            intentHint
                                                    .map(AssistantIntentHintResolver.IntentHint::actionType)
                                                    .orElse(null)
                                    )
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

    private String systemPrompt(
            Optional<AssistantIntentHintResolver.IntentHint> intentHint
    ) {
        String hintRule =
                intentHint
                        .map(
                                hint ->
                                        "- INTENT_HINT es de alta confianza y restringe esta peticion a "
                                                + hint.actionType()
                                                + ". Usa esa familia de accion y completa solo sus operandos."
                        )
                        .orElse(
                                "- No hay INTENT_HINT: interpreta libremente la familia de accion solicitada."
                        );

        return """
                Eres el interprete UML de ClassForge.
                Devuelve solo la intencion semantica solicitada usando el JSON Schema.

                REGLAS CRITICAS:
                - PETICION_USUARIO es la unica fuente de acciones solicitadas.
                - MODELO_ACTUAL_SOLO_LECTURA es evidencia para resolver nombres y estado existente; NUNCA copies sus clases, atributos o relaciones como acciones nuevas salvo que PETICION_USUARIO lo pida explicitamente.
                - No conviertas atributos visibles del modelo actual en ADD_ATTRIBUTES por el simple hecho de aparecer en contexto.
                %s

                REGLAS UML:
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
                - Si no se especifica tipo de relacion al crearla, ASSOCIATION.
                - Si no se especifica multiplicidad al crearla, 1..1 en ambos extremos.
                - En UPDATE_RELATIONSHIP solo emite las multiplicidades/tipo que el usuario pidio cambiar; Java preserva el resto.
                - No inventes clases, atributos ni acciones no solicitadas.
                - resolvedClassMentions contiene referencias existentes detectadas de forma tolerante a errores tipograficos.
                - Para referencias existentes usa exactamente canonicalName cuando aparezca en resolvedClassMentions.
                - Un typo del usuario como 4nimal puede representar Animal; no copies el typo si hay canonicalName.
                - CREATE_CLASS crea un simbolo nuevo: ahi conserva el nombre pedido y no lo autocorrijas contra clases existentes.

                EJEMPLOS:
                "Crea Veterinario y ponle id UUID y nombre"
                -> CREATE_CLASS Veterinario con id UUID EXPLICIT y nombre STRING DEFAULT.

                "Renombra Veterinario a MedicoVeterinario"
                -> RENAME_CLASS className=Veterinario newName=MedicoVeterinario.

                "A Veterinario agregale telefono y correo"
                -> ADD_ATTRIBUTES Veterinario: telefono STRING DEFAULT, correo STRING DEFAULT.

                "En Mascota renombra el atributo peso a pesoKg"
                -> UPDATE_ATTRIBUTE className=Mascota attributeName=peso newAttributeName=pesoKg.

                "Conecta Animal con Veterinario"
                -> CREATE_RELATIONSHIP Animal -> Veterinario, ASSOCIATION, 1..1 a 1..1.

                "En la relacion entre Propietario y Mascota cambia la multiplicidad de Mascota a 0..*"
                -> UPDATE_RELATIONSHIP source=Propietario target=Mascota targetLower=0 targetUpper=-1.
                """.formatted(hintRule);
    }

    private String userPrompt(
            String userText,
            ProjectDocument document,
            Optional<AssistantIntentHintResolver.IntentHint> intentHint
    ) throws Exception {
        return """
                PETICION_USUARIO:
                %s

                MODELO_ACTUAL_SOLO_LECTURA:
                %s
                """
                .formatted(
                        userText,
                        compactContext(
                                userText,
                                document,
                                intentHint
                        )
                );
    }

    String compactContext(
            String userText,
            ProjectDocument document
    ) throws Exception {
        return compactContext(
                userText,
                document,
                intentHintResolver.resolve(userText, document)
        );
    }

    private String compactContext(
            String userText,
            ProjectDocument document,
            Optional<AssistantIntentHintResolver.IntentHint> intentHint
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

        List<AssistantEntityReferenceResolver.ResolvedClassReference> resolvedMentions =
                entityResolver.resolveMentions(
                        userText,
                        document
                );

        LinkedHashSet<UUID> directIds =
                resolvedMentions.stream()
                        .map(
                                AssistantEntityReferenceResolver.ResolvedClassReference::classId
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
                                umlClass ->
                                        focusedClass(
                                                umlClass,
                                                includeAttributes(intentHint)
                                        )
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
                includeRelationships(intentHint)
                        ? allRelationships.stream()
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
                        .toList()
                        : List.of();

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

        intentHint.ifPresent(
                hint ->
                        context.put(
                                "intentHint",
                                Map.of(
                                        "actionType",
                                        hint.actionType().name(),
                                        "confidence",
                                        hint.confidence(),
                                        "evidence",
                                        hint.evidence(),
                                        "advisory",
                                        true
                                )
                        )
        );

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

        if (!resolvedMentions.isEmpty()) {
            context.put(
                    "resolvedClassMentions",
                    resolvedMentions.stream()
                            .map(
                                    mention ->
                                            Map.of(
                                                    "observed",
                                                    mention.observedText(),
                                                    "canonicalName",
                                                    mention.canonicalName(),
                                                    "confidence",
                                                    Math.round(
                                                            mention.score()
                                                                    * 100.0d
                                                    )
                                                            / 100.0d
                                            )
                            )
                            .toList()
            );
        }

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
            UmlClass umlClass,
            boolean includeAttributes
    ) {
        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "name",
                umlClass.name()
        );

        if (
                includeAttributes
                        && !umlClass.attributes().isEmpty()
        ) {
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

    private boolean includeAttributes(
            Optional<AssistantIntentHintResolver.IntentHint> intentHint
    ) {
        if (intentHint.isEmpty()) {
            return true;
        }

        return switch (intentHint.get().actionType()) {
            case ADD_ATTRIBUTES,
                 UPDATE_ATTRIBUTE,
                 DELETE_ATTRIBUTE -> true;
            default -> false;
        };
    }

    private boolean includeRelationships(
            Optional<AssistantIntentHintResolver.IntentHint> intentHint
    ) {
        if (intentHint.isEmpty()) {
            return true;
        }

        return switch (intentHint.get().actionType()) {
            case CREATE_RELATIONSHIP,
                 UPDATE_RELATIONSHIP,
                 DELETE_RELATIONSHIP -> true;
            default -> false;
        };
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
        return schema(null);
    }

    Map<String, Object> schema(
            AssistantActionType forcedType
    ) {
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

        List<String> allowedActionTypes =
                forcedType == null
                        ? List.of(
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
                        : List.of(
                        forcedType.name()
                );

        actionProperties.put(
                "type",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        allowedActionTypes
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

        List<String> requiredActionFields =
                new java.util.ArrayList<>();
        requiredActionFields.add("type");

        if (forcedType != null) {
            switch (forcedType) {
                case CREATE_CLASS -> {
                    actionProperties.put("className", Map.of("type", "string"));
                    requiredActionFields.add("className");
                }
                case RENAME_CLASS -> {
                    actionProperties.put("className", Map.of("type", "string"));
                    actionProperties.put("newName", Map.of("type", "string"));
                    requiredActionFields.add("className");
                    requiredActionFields.add("newName");
                }
                case DELETE_CLASS -> {
                    actionProperties.put("className", Map.of("type", "string"));
                    requiredActionFields.add("className");
                }
                case ADD_ATTRIBUTES -> {
                    actionProperties.put("className", Map.of("type", "string"));
                    requiredActionFields.add("className");
                    requiredActionFields.add("attributes");
                }
                case UPDATE_ATTRIBUTE, DELETE_ATTRIBUTE -> {
                    actionProperties.put("className", Map.of("type", "string"));
                    actionProperties.put("attributeName", Map.of("type", "string"));
                    requiredActionFields.add("className");
                    requiredActionFields.add("attributeName");
                }
                case CREATE_RELATIONSHIP, UPDATE_RELATIONSHIP, DELETE_RELATIONSHIP -> {
                    actionProperties.put("sourceClassName", Map.of("type", "string"));
                    actionProperties.put("targetClassName", Map.of("type", "string"));
                    requiredActionFields.add("sourceClassName");
                    requiredActionFields.add("targetClassName");
                }
            }
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
                List.copyOf(
                        requiredActionFields
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
                                forcedType == null ? 30 : 1,
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