package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantAttributePlan;
import com.classforge.assistant.AssistantEntityReferenceResolver;
import com.classforge.assistant.AssistantPlanAction;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.AssistantTypeSource;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class UmlToolCallResolver {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern EXPLICIT_MULTIPLICITY = Pattern.compile("(?i)([01])\\s*\\.\\.\\s*(\\*|[01])");

    private final AssistantEntityReferenceResolver entityResolver;
    private final AssistantLiteralArgumentBinder literalBinder;

    public UmlToolCallResolver(
            AssistantEntityReferenceResolver entityResolver,
            AssistantLiteralArgumentBinder literalBinder
    ) {
        this.entityResolver = entityResolver;
        this.literalBinder = literalBinder;
    }

    public AssistantToolResolution resolve(
            String userText,
            List<AssistantToolInvocation> invocations,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        if (invocations == null || invocations.isEmpty()) {
            throw new AssistantPlanningException("El modelo no produjo tool calls UML.");
        }
        if (invocations.size() > 30) {
            throw new AssistantPlanningException("El modelo produjo mas de 30 tool calls UML.");
        }

        Set<AssistantToolName> exposed = catalog.definitions().stream()
                .map(AssistantToolDefinition::name)
                .collect(java.util.stream.Collectors.toSet());

        List<AssistantPlanAction> actions = new ArrayList<>();
        List<AssistantResolvedReference> references = new ArrayList<>();
        List<String> summaries = new ArrayList<>();

        for (AssistantToolInvocation invocation : invocations) {
            if (!exposed.contains(invocation.name())) {
                throw new AssistantPlanningException(
                        "Qwen intento usar una tool no expuesta para esta peticion: " + invocation.name().wireName()
                );
            }

            ResolvedAction resolved = resolveInvocation(userText, invocation, catalog, document);
            actions.add(resolved.action());
            references.addAll(resolved.references());
            summaries.add(resolved.summary());
        }

        return new AssistantToolResolution(
                new AssistantSemanticPlan(String.join("; ", summaries), List.copyOf(actions)),
                List.copyOf(references)
        );
    }

    private ResolvedAction resolveInvocation(
            String userText,
            AssistantToolInvocation invocation,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        JsonNode args = invocation.arguments();
        if (args == null || !args.isObject()) {
            throw new AssistantPlanningException(
                    "Los argumentos de " + invocation.name().wireName() + " no son un objeto JSON."
            );
        }

        return switch (invocation.name()) {
            case ROUTE_REQUEST -> throw new AssistantPlanningException("route_uml_request es control de routing y no produce una accion UML.");
            case CREATE_CLASS -> createClass(userText, args);
            case CREATE_ASSOCIATION_CLASS -> createAssociationClass(userText, args, catalog, document);
            case RENAME_CLASS -> renameClass(userText, args, catalog, document);
            case DELETE_CLASS -> deleteClass(userText, args, catalog, document);
            case ADD_ATTRIBUTES -> addAttributes(userText, args, catalog, document);
            case RENAME_ATTRIBUTE -> renameAttribute(userText, args, catalog, document);
            case UPDATE_ATTRIBUTE_PROPERTIES -> updateAttributeProperties(userText, args, catalog, document);
            case DELETE_ATTRIBUTE -> deleteAttribute(userText, args, catalog, document);
            case CREATE_ASSOCIATION -> createRelationship(userText, args, catalog, document, invocation.name(),
                    "source_class", "target_class", UmlRelationshipType.ASSOCIATION);
            case CREATE_AGGREGATION -> createRelationship(userText, args, catalog, document, invocation.name(),
                    "whole_class", "part_class", UmlRelationshipType.AGGREGATION);
            case CREATE_COMPOSITION -> createRelationship(userText, args, catalog, document, invocation.name(),
                    "whole_class", "part_class", UmlRelationshipType.COMPOSITION);
            case CREATE_GENERALIZATION -> createRelationship(userText, args, catalog, document, invocation.name(),
                    "subclass", "superclass", UmlRelationshipType.GENERALIZATION);
            case SET_RELATIONSHIP_MULTIPLICITY -> setRelationshipMultiplicity(userText, args, catalog, document);
            case CHANGE_RELATIONSHIP_TYPE -> changeRelationshipType(userText, args, catalog, document);
            case DELETE_RELATIONSHIP -> deleteRelationship(userText, args, catalog, document);
            case FINISH_PLAN -> throw new AssistantPlanningException("finish_plan es control de flujo y no produce una accion UML.");
        };
    }

    private ResolvedAction createClass(String userText, JsonNode args) {
        String newName = literalBinder.bindNewIdentifier(userText, requiredText(args, "name"));
        List<AssistantAttributePlan> attributes = List.of();

        AssistantPlanAction action = new AssistantPlanAction(
                AssistantToolName.CREATE_CLASS.actionType(),
                newName,
                null,
                attributes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(action, List.of(), "Crear clase " + newName);
    }

    private ResolvedAction createAssociationClass(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingRelationship selected = existingRelationship(
                requiredText(args, "existing_relationship_ref"), catalog
        );
        ExistingRelationship existing = bindRelationshipReference(
                userText, selected, catalog, document
        );
        String newName = literalBinder.bindNewIdentifier(
                userText, requiredText(args, "name")
        );
        List<AssistantAttributePlan> attributes = attributes(args.get("attributes"));

        AssistantPlanAction action = new AssistantPlanAction(
                AssistantToolName.CREATE_ASSOCIATION_CLASS.actionType(),
                newName,
                null,
                attributes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                existing.sourceClassName(),
                existing.targetClassName(),
                null,
                null,
                null,
                null,
                null,
                existing.id()
        );
        return new ResolvedAction(
                action,
                List.of(existing.reference()),
                "Crear clase de asociacion " + newName + " sobre "
                        + existing.sourceClassName() + " - " + existing.targetClassName()
        );
    }

    private ResolvedAction renameClass(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingClass selected = existingClass(requiredText(args, "existing_class"), catalog, document);
        ExistingClass existing = bindUnaryClassReference(userText, selected, catalog, document);
        String newName = literalBinder.bindNewIdentifier(userText, requiredText(args, "new_name"));

        AssistantPlanAction action = emptyAction(
                AssistantToolName.RENAME_CLASS,
                existing.name(),
                newName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new ResolvedAction(action, List.of(existing.reference()), "Renombrar " + existing.name() + " a " + newName);
    }

    private ResolvedAction deleteClass(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingClass selected = existingClass(requiredText(args, "existing_class"), catalog, document);
        ExistingClass existing = bindUnaryClassReference(userText, selected, catalog, document);
        AssistantPlanAction action = emptyAction(
                AssistantToolName.DELETE_CLASS,
                existing.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new ResolvedAction(action, List.of(existing.reference()), "Eliminar clase " + existing.name());
    }

    private ResolvedAction addAttributes(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingClass selected = existingClass(requiredText(args, "existing_class"), catalog, document);
        ExistingClass existing = bindUnaryClassReference(userText, selected, catalog, document);
        List<AssistantAttributePlan> attributes = attributes(args.get("attributes"));
        if (attributes.isEmpty()) {
            throw new AssistantPlanningException("add_attributes requiere al menos un atributo nuevo.");
        }

        AssistantPlanAction action = new AssistantPlanAction(
                AssistantToolName.ADD_ATTRIBUTES.actionType(),
                existing.name(),
                null,
                attributes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new ResolvedAction(action, List.of(existing.reference()), "Agregar atributos a " + existing.name());
    }

    private ResolvedAction renameAttribute(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingAttribute existing = existingAttribute(requiredText(args, "existing_attribute_ref"), catalog, document);
        requireGroundedAttribute(userText, existing, document);
        String newName = literalBinder.bindNewIdentifier(userText, requiredText(args, "new_name"));

        AssistantPlanAction action = new AssistantPlanAction(
                AssistantToolName.RENAME_ATTRIBUTE.actionType(),
                existing.className(),
                null,
                List.of(),
                existing.attributeName(),
                newName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(
                action,
                List.of(existing.reference()),
                "Renombrar atributo " + existing.className() + "." + existing.attributeName() + " a " + newName
        );
    }

    private ResolvedAction updateAttributeProperties(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingAttribute existing = existingAttribute(requiredText(args, "existing_attribute_ref"), catalog, document);
        requireGroundedAttribute(userText, existing, document);

        UmlDataType dataType = optionalEnum(args, "data_type", UmlDataType.class);
        String customTypeName = optionalText(args, "custom_type_name");
        UmlVisibility visibility = optionalEnum(args, "visibility", UmlVisibility.class);
        Boolean nullable = optionalBoolean(args, "nullable");
        Boolean identifier = optionalBoolean(args, "identifier");

        if (dataType == null && customTypeName == null && visibility == null && nullable == null && identifier == null) {
            throw new AssistantPlanningException("update_attribute_properties no contiene ningun cambio solicitado.");
        }

        AssistantPlanAction action = new AssistantPlanAction(
                AssistantToolName.UPDATE_ATTRIBUTE_PROPERTIES.actionType(),
                existing.className(),
                null,
                List.of(),
                existing.attributeName(),
                null,
                dataType,
                customTypeName,
                visibility,
                nullable,
                identifier,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(action, List.of(existing.reference()), "Actualizar atributo " + existing.className() + "." + existing.attributeName());
    }

    private ResolvedAction deleteAttribute(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingAttribute existing = existingAttribute(requiredText(args, "existing_attribute_ref"), catalog, document);
        requireGroundedAttribute(userText, existing, document);

        AssistantPlanAction action = emptyAction(
                AssistantToolName.DELETE_ATTRIBUTE,
                existing.className(),
                null,
                existing.attributeName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(action, List.of(existing.reference()), "Eliminar atributo " + existing.className() + "." + existing.attributeName());
    }

    private ResolvedAction createRelationship(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document,
            AssistantToolName tool,
            String sourceField,
            String targetField,
            UmlRelationshipType relationshipType
    ) {
        ExistingClass selectedSource = existingClass(requiredText(args, sourceField), catalog, document);
        ExistingClass selectedTarget = existingClass(requiredText(args, targetField), catalog, document);
        List<AssistantEntityReferenceResolver.ResolvedClassReference> orderedMentions = orderedDistinctClassMentions(userText, document);
        ExistingClass source = selectedSource;
        ExistingClass target = selectedTarget;
        if (orderedMentions.size() == 2) {
            source = existingClass(orderedMentions.get(0).canonicalName(), catalog, document);
            target = existingClass(orderedMentions.get(1).canonicalName(), catalog, document);
        } else {
            requireGroundedClass(userText, source);
            requireGroundedClass(userText, target);
        }
        if (source.id().equals(target.id())) {
            throw new AssistantPlanningException("La relacion requiere dos extremos UML distintos.");
        }

        AssistantPlanAction action = emptyAction(
                tool,
                null,
                null,
                null,
                null,
                source.name(),
                target.name(),
                relationshipType,
                optionalLower(args, "source_lower"),
                optionalUpper(args, "source_upper"),
                optionalLower(args, "target_lower"),
                optionalUpper(args, "target_upper"),
                null,
                null
        );

        return new ResolvedAction(
                action,
                List.of(source.reference(), target.reference()),
                "Crear " + relationshipType + " " + source.name() + " -> " + target.name()
        );
    }

    private ResolvedAction setRelationshipMultiplicity(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingRelationship selectedRelationship = existingRelationship(requiredText(args, "existing_relationship_ref"), catalog);
        ExistingRelationship existing = bindRelationshipReference(userText, selectedRelationship, catalog, document);
        ExistingClass selectedEnd = existingClass(requiredText(args, "end_class"), catalog, document);
        ExistingClass end = bindMultiplicityEndpoint(userText, existing, selectedEnd, catalog, document);

        int lower = requiredInteger(args, "lower");
        int upper = requiredInteger(args, "upper");
        MultiplicityValues groundedMultiplicity = bindMultiplicityValues(userText, lower, upper);
        lower = groundedMultiplicity.lower();
        upper = groundedMultiplicity.upper();
        validateMultiplicity(lower, upper);

        boolean sourceEnd = existing.sourceClassName().equals(end.name());
        boolean targetEnd = existing.targetClassName().equals(end.name());
        if (!sourceEnd && !targetEnd) {
            throw new AssistantPlanningException(
                    "end_class debe ser uno de los extremos de la relacion seleccionada: "
                            + existing.sourceClassName() + " o " + existing.targetClassName()
            );
        }

        AssistantPlanAction action = emptyAction(
                AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY,
                null,
                null,
                null,
                null,
                existing.sourceClassName(),
                existing.targetClassName(),
                null,
                sourceEnd ? lower : null,
                sourceEnd ? upper : null,
                targetEnd ? lower : null,
                targetEnd ? upper : null,
                null,
                null
        );

        return new ResolvedAction(
                action,
                List.of(existing.reference(), end.reference()),
                "Cambiar multiplicidad de " + end.name() + " en " + existing.sourceClassName() + " -> " + existing.targetClassName()
        );
    }

    private ResolvedAction changeRelationshipType(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingRelationship selectedRelationship = existingRelationship(requiredText(args, "existing_relationship_ref"), catalog);
        ExistingRelationship existing = bindRelationshipReference(userText, selectedRelationship, catalog, document);
        UmlRelationshipType relationshipType = requiredEnum(args, "relationship_type", UmlRelationshipType.class);

        AssistantPlanAction action = emptyAction(
                AssistantToolName.CHANGE_RELATIONSHIP_TYPE,
                null,
                null,
                null,
                null,
                existing.sourceClassName(),
                existing.targetClassName(),
                relationshipType,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(action, List.of(existing.reference()), "Cambiar tipo de relacion a " + relationshipType);
    }

    private ResolvedAction deleteRelationship(
            String userText,
            JsonNode args,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingRelationship selectedRelationship = existingRelationship(requiredText(args, "existing_relationship_ref"), catalog);
        ExistingRelationship existing = bindRelationshipReference(userText, selectedRelationship, catalog, document);

        AssistantPlanAction action = emptyAction(
                AssistantToolName.DELETE_RELATIONSHIP,
                null,
                null,
                null,
                null,
                existing.sourceClassName(),
                existing.targetClassName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        return new ResolvedAction(action, List.of(existing.reference()), "Eliminar relacion " + existing.sourceClassName() + " -> " + existing.targetClassName());
    }

    private ExistingClass bindUnaryClassReference(
            String userText,
            ExistingClass selected,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions = distinctClassMentions(userText, document);
        if (mentions.size() == 1) {
            AssistantEntityReferenceResolver.ResolvedClassReference grounded = mentions.getFirst();
            return existingClass(grounded.canonicalName(), catalog, document);
        }

        requireGroundedClass(userText, selected);
        return selected;
    }

    private ExistingRelationship bindRelationshipReference(
            String userText,
            ExistingRelationship selected,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        Set<String> groundedClasses = distinctClassMentions(userText, document).stream()
                .map(AssistantEntityReferenceResolver.ResolvedClassReference::canonicalName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (groundedClasses.size() >= 2) {
            List<AssistantToolCatalog.RelationshipReference> matches = catalog.relationshipsByLabel().values().stream()
                    .filter(reference -> groundedClasses.contains(reference.sourceClassName()))
                    .filter(reference -> groundedClasses.contains(reference.targetClassName()))
                    .toList();

            if (matches.size() == 1) {
                return existingRelationship(matches.getFirst().label(), catalog);
            }

            if (matches.size() > 1) {
                boolean selectedMatches = matches.stream()
                        .anyMatch(reference -> reference.relationshipId().equals(selected.id()));
                if (!selectedMatches) {
                    throw new AssistantPlanningException(
                            "La peticion menciona extremos con mas de una relacion posible y la tool eligio otra relacion."
                    );
                }
                return selected;
            }
        }

        requireGroundedRelationship(userText, selected);
        return selected;
    }

    private ExistingClass bindMultiplicityEndpoint(
            String userText,
            ExistingRelationship relationship,
            ExistingClass selected,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        ExistingClass source = existingClass(relationship.sourceClassName(), catalog, document);
        ExistingClass target = existingClass(relationship.targetClassName(), catalog, document);

        int sourceScore = multiplicityEndpointScore(userText, source, document);
        int targetScore = multiplicityEndpointScore(userText, target, document);

        if (sourceScore > targetScore && sourceScore > 0) {
            return source;
        }
        if (targetScore > sourceScore && targetScore > 0) {
            return target;
        }

        if (!selected.id().equals(source.id()) && !selected.id().equals(target.id())) {
            throw new AssistantPlanningException(
                    "end_class debe ser uno de los extremos de la relacion seleccionada: "
                            + relationship.sourceClassName() + " o " + relationship.targetClassName()
            );
        }
        requireGroundedClass(userText, selected);
        return selected;
    }

    private int multiplicityEndpointScore(
            String userText,
            ExistingClass endpoint,
            ProjectDocument document
    ) {
        List<String> tokens = normalizedTokens(userText);
        if (tokens.isEmpty()) {
            return 0;
        }

        return distinctClassMentions(userText, document).stream()
                .filter(reference -> reference.classId().equals(endpoint.id()))
                .mapToInt(reference -> cueScoreAround(tokens, reference.tokenIndex()))
                .max()
                .orElse(0);
    }

    private int cueScoreAround(List<String> tokens, int index) {
        int score = 0;
        int from = Math.max(0, index - 4);
        int to = Math.min(tokens.size() - 1, index + 4);
        for (int i = from; i <= to; i++) {
            String token = tokens.get(i);
            int distance = Math.abs(i - index);
            int proximity = Math.max(1, 5 - distance);
            if (token.startsWith("multiplic")) {
                score += 6 * proximity;
            } else if (token.equals("lado")) {
                score += 5 * proximity;
            } else if (token.startsWith("much")) {
                score += 4 * proximity;
            } else if (token.startsWith("vari")) {
                score += 4 * proximity;
            } else if (token.startsWith("ningun")) {
                score += 4 * proximity;
            } else if (token.equals("cero")) {
                score += 4 * proximity;
            } else if (token.equals("0..*") || token.equals("1..*") || token.equals("0..1") || token.equals("1..1")) {
                score += 3 * proximity;
            }
        }
        return score;
    }

    private MultiplicityValues bindMultiplicityValues(String userText, int selectedLower, int selectedUpper) {
        String normalized = normalizeText(userText);
        java.util.regex.Matcher explicit = EXPLICIT_MULTIPLICITY.matcher(userText == null ? "" : userText);
        if (explicit.find()) {
            int lower = Integer.parseInt(explicit.group(1));
            int upper = "*".equals(explicit.group(2)) ? -1 : Integer.parseInt(explicit.group(2));
            return new MultiplicityValues(lower, upper);
        }

        boolean zero = normalized.contains("cero") || normalized.contains("ninguna") || normalized.contains("ningun");
        boolean many = normalized.contains("muchas") || normalized.contains("muchos")
                || normalized.contains("varias") || normalized.contains("varios");
        if (zero && many) {
            return new MultiplicityValues(0, -1);
        }
        return new MultiplicityValues(selectedLower, selectedUpper);
    }

    private List<AssistantEntityReferenceResolver.ResolvedClassReference> orderedDistinctClassMentions(
            String userText,
            ProjectDocument document
    ) {
        return distinctClassMentions(userText, document).stream()
                .sorted(Comparator.comparingInt(AssistantEntityReferenceResolver.ResolvedClassReference::tokenIndex))
                .toList();
    }

    private String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_*\\s.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record MultiplicityValues(int lower, int upper) { }

    private List<AssistantEntityReferenceResolver.ResolvedClassReference> distinctClassMentions(
            String userText,
            ProjectDocument document
    ) {
        Map<UUID, AssistantEntityReferenceResolver.ResolvedClassReference> byId = new java.util.LinkedHashMap<>();
        for (AssistantEntityReferenceResolver.ResolvedClassReference reference : entityResolver.resolveMentions(userText, document)) {
            byId.putIfAbsent(reference.classId(), reference);
        }
        return List.copyOf(byId.values());
    }

    private List<String> normalizedTokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String normalized = DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.*]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isBlank() ? List.of() : List.of(normalized.split(" "));
    }

    private void requireGroundedClass(String userText, ExistingClass existing) {
        if (!entityResolver.fuzzyMentions(userText, existing.name())) {
            throw new AssistantPlanningException(
                    "La clase seleccionada por la tool no esta respaldada por la peticion: " + existing.name()
            );
        }
    }

    private void requireGroundedAttribute(
            String userText,
            ExistingAttribute existing,
            ProjectDocument document
    ) {
        if (!entityResolver.fuzzyMentions(userText, existing.className())
                || !entityResolver.fuzzyMentionsExistingAttribute(
                userText,
                existing.className(),
                existing.attributeName(),
                document
        )) {
            throw new AssistantPlanningException(
                    "El atributo seleccionado por la tool no esta respaldado por la peticion: "
                            + existing.className() + "." + existing.attributeName()
            );
        }
    }

    private void requireGroundedRelationship(String userText, ExistingRelationship existing) {
        if (!entityResolver.fuzzyMentions(userText, existing.sourceClassName())
                || !entityResolver.fuzzyMentions(userText, existing.targetClassName())) {
            throw new AssistantPlanningException(
                    "La relacion seleccionada por la tool no esta respaldada por ambos extremos mencionados por el usuario."
            );
        }
    }

    private ExistingClass existingClass(
            String observed,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        UUID exact = catalog.classIdsByName().get(observed);
        if (exact != null) {
            return new ExistingClass(observed, exact, new AssistantResolvedReference(
                    AssistantResolvedReference.ReferenceKind.CLASS,
                    observed,
                    observed,
                    exact,
                    1.0d,
                    null
            ));
        }

        AssistantEntityReferenceResolver.ResolvedClassReference fuzzy = entityResolver
                .resolveExistingClass(observed, document)
                .orElseThrow(() -> new AssistantPlanningException(
                        "Referencia de clase inexistente o ambigua: '" + observed + "'."
                ));

        return new ExistingClass(fuzzy.canonicalName(), fuzzy.classId(), new AssistantResolvedReference(
                AssistantResolvedReference.ReferenceKind.CLASS,
                observed,
                fuzzy.canonicalName(),
                fuzzy.classId(),
                fuzzy.score(),
                null
        ));
    }

    private ExistingAttribute existingAttribute(
            String observed,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        AssistantToolCatalog.AttributeReference exact = catalog.attributesByLabel().get(observed);
        if (exact != null) {
            return new ExistingAttribute(
                    exact.className(),
                    exact.attributeName(),
                    exact.attributeId(),
                    new AssistantResolvedReference(
                            AssistantResolvedReference.ReferenceKind.ATTRIBUTE,
                            observed,
                            exact.label(),
                            exact.attributeId(),
                            1.0d,
                            exact.className()
                    )
            );
        }

        int separator = observed.indexOf('.');
        if (separator <= 0 || separator >= observed.length() - 1) {
            throw new AssistantPlanningException(
                    "Referencia de atributo inexistente: '" + observed + "'. Usa una referencia real Class.attribute."
            );
        }

        String classCandidate = observed.substring(0, separator);
        String attributeCandidate = observed.substring(separator + 1);
        AssistantEntityReferenceResolver.ResolvedClassReference resolvedClass = entityResolver
                .resolveExistingClass(classCandidate, document)
                .orElseThrow(() -> new AssistantPlanningException(
                        "Clase inexistente o ambigua en la referencia de atributo: '" + observed + "'."
                ));
        AssistantEntityReferenceResolver.ResolvedAttributeReference resolvedAttribute = entityResolver
                .resolveExistingAttribute(resolvedClass.canonicalName(), attributeCandidate, document)
                .orElseThrow(() -> new AssistantPlanningException(
                        "Atributo inexistente o ambiguo en '" + observed + "'."
                ));

        String canonicalLabel = resolvedClass.canonicalName() + "." + resolvedAttribute.canonicalName();
        return new ExistingAttribute(
                resolvedClass.canonicalName(),
                resolvedAttribute.canonicalName(),
                resolvedAttribute.attributeId(),
                new AssistantResolvedReference(
                        AssistantResolvedReference.ReferenceKind.ATTRIBUTE,
                        observed,
                        canonicalLabel,
                        resolvedAttribute.attributeId(),
                        Math.min(resolvedClass.score(), resolvedAttribute.score()),
                        resolvedClass.canonicalName()
                )
        );
    }

    private ExistingRelationship existingRelationship(String observed, AssistantToolCatalog catalog) {
        AssistantToolCatalog.RelationshipReference exact = catalog.relationshipsByLabel().get(observed);
        if (exact == null) {
            throw new AssistantPlanningException("Referencia de relacion inexistente: '" + observed + "'.");
        }

        return new ExistingRelationship(
                exact.sourceClassName(),
                exact.targetClassName(),
                exact.relationshipId(),
                new AssistantResolvedReference(
                        AssistantResolvedReference.ReferenceKind.RELATIONSHIP,
                        observed,
                        exact.label(),
                        exact.relationshipId(),
                        1.0d,
                        exact.sourceClassName() + " -> " + exact.targetClassName()
                )
        );
    }

    private List<AssistantAttributePlan> attributes(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new AssistantPlanningException("attributes debe ser un array JSON.");
        }

        List<AssistantAttributePlan> result = new ArrayList<>();
        for (JsonNode attribute : node) {
            result.add(new AssistantAttributePlan(
                    requiredText(attribute, "name"),
                    optionalEnum(attribute, "data_type", UmlDataType.class),
                    optionalText(attribute, "custom_type_name"),
                    optionalEnum(attribute, "visibility", UmlVisibility.class),
                    optionalBoolean(attribute, "nullable"),
                    optionalBoolean(attribute, "identifier"),
                    optionalEnum(attribute, "type_source", AssistantTypeSource.class)
            ));
        }
        return List.copyOf(result);
    }

    private AssistantPlanAction emptyAction(
            AssistantToolName tool,
            String className,
            String newName,
            String attributeName,
            String newAttributeName,
            String sourceClassName,
            String targetClassName,
            UmlRelationshipType relationshipType,
            Integer sourceLower,
            Integer sourceUpper,
            Integer targetLower,
            Integer targetUpper,
            UmlDataType dataType,
            String customTypeName
    ) {
        return new AssistantPlanAction(
                tool.actionType(),
                className,
                newName,
                List.of(),
                attributeName,
                newAttributeName,
                dataType,
                customTypeName,
                null,
                null,
                null,
                sourceClassName,
                targetClassName,
                relationshipType,
                sourceLower,
                sourceUpper,
                targetLower,
                targetUpper
        );
    }

    private void validateMultiplicity(int lower, int upper) {
        if (lower < 0) {
            throw new AssistantPlanningException("La multiplicidad lower no puede ser negativa.");
        }
        if (upper < -1) {
            throw new AssistantPlanningException("La multiplicidad upper solo admite -1 o valores no negativos.");
        }
        if (upper != -1 && lower > upper) {
            throw new AssistantPlanningException("La multiplicidad lower no puede superar upper.");
        }
    }

    private String requiredText(JsonNode object, String field) {
        String value = optionalText(object, field);
        if (value == null) {
            throw new AssistantPlanningException("Falta argumento obligatorio '" + field + "'.");
        }
        return value;
    }

    private String optionalText(JsonNode object, String field) {
        JsonNode node = object == null ? null : object.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int requiredInteger(JsonNode object, String field) {
        Integer value = optionalInteger(object, field);
        if (value == null) {
            throw new AssistantPlanningException("Falta argumento obligatorio '" + field + "'.");
        }
        return value;
    }

    private Integer optionalLower(JsonNode object, String field) {
        Integer value = optionalInteger(object, field);
        if (value != null && value < 0) {
            throw new AssistantPlanningException("El argumento '" + field + "' no puede ser negativo.");
        }
        return value;
    }

    private Integer optionalUpper(JsonNode object, String field) {
        Integer value = optionalInteger(object, field);
        if (value != null && value < -1) {
            throw new AssistantPlanningException("El argumento '" + field + "' solo admite -1 o valores no negativos.");
        }
        return value;
    }

    private Integer optionalInteger(JsonNode object, String field) {
        JsonNode node = object == null ? null : object.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return Integer.valueOf(node.asString());
        } catch (NumberFormatException exception) {
            throw new AssistantPlanningException("El argumento '" + field + "' no es un entero.", exception);
        }
    }

    private Boolean optionalBoolean(JsonNode object, String field) {
        JsonNode node = object == null ? null : object.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new AssistantPlanningException("El argumento '" + field + "' no es booleano.");
    }

    private <E extends Enum<E>> E requiredEnum(JsonNode object, String field, Class<E> type) {
        E value = optionalEnum(object, field, type);
        if (value == null) {
            throw new AssistantPlanningException("Falta argumento obligatorio '" + field + "'.");
        }
        return value;
    }

    private <E extends Enum<E>> E optionalEnum(JsonNode object, String field, Class<E> type) {
        String value = optionalText(object, field);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AssistantPlanningException("Valor invalido para '" + field + "': " + value, exception);
        }
    }

    private record ResolvedAction(
            AssistantPlanAction action,
            List<AssistantResolvedReference> references,
            String summary
    ) {
    }

    private record ExistingClass(
            String name,
            UUID id,
            AssistantResolvedReference reference
    ) {
    }

    private record ExistingAttribute(
            String className,
            String attributeName,
            UUID id,
            AssistantResolvedReference reference
    ) {
    }

    private record ExistingRelationship(
            String sourceClassName,
            String targetClassName,
            UUID id,
            AssistantResolvedReference reference
    ) {
    }
}
