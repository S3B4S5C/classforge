package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantEntityReferenceResolver;
import com.classforge.assistant.AssistantIntentHintResolver;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AssistantToolRouteAdjudicator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern ADD_TO_EXISTING_CLASS = Pattern.compile(
            "\\bpon(?:le)?\\b.+\\b(?:a|en)\\s+(?:la\\s+)?clase\\b"
    );

    private final AssistantIntentHintResolver intentHintResolver;
    private final AssistantEntityReferenceResolver entityResolver;
    private final AssistantCompoundRequestDetector compoundDetector;

    public AssistantToolRouteAdjudicator(
            AssistantIntentHintResolver intentHintResolver,
            AssistantEntityReferenceResolver entityResolver,
            AssistantCompoundRequestDetector compoundDetector
    ) {
        this.intentHintResolver = intentHintResolver;
        this.entityResolver = entityResolver;
        this.compoundDetector = compoundDetector;
    }

    public List<AssistantToolName> adjudicate(
            String userText,
            ProjectDocument document,
            List<AssistantToolName> proposed
    ) {
        List<AssistantToolName> uniqueProposed = uniqueExecutable(proposed);

        if (compoundDetector.isCompound(userText)) {
            List<AssistantToolName> compound = compoundRoute(userText);
            if (!compound.isEmpty()) {
                return compound;
            }
            return uniqueProposed;
        }

        AssistantToolName strong = strongSimpleRoute(userText, document);
        if (strong != null) {
            return List.of(strong);
        }

        if (uniqueProposed.isEmpty()) {
            return List.of();
        }

        // A simple request must never gain extra side effects merely because
        // the semantic router emitted redundant/adjacent steps.
        return List.of(uniqueProposed.getFirst());
    }

    private AssistantToolName strongSimpleRoute(String userText, ProjectDocument document) {
        String text = normalize(userText);
        List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions = entityResolver.resolveMentions(userText, document);

        Optional<AssistantIntentHintResolver.IntentHint> hint = intentHintResolver.resolve(userText, document);

        // Attribute-scoped intents are already project-aware and should beat
        // generic class rename/add cues.
        if (hint.isPresent() && hint.get().actionType() == AssistantActionType.UPDATE_ATTRIBUTE) {
            return isAttributeRename(text)
                    ? AssistantToolName.RENAME_ATTRIBUTE
                    : AssistantToolName.UPDATE_ATTRIBUTE_PROPERTIES;
        }
        if (hint.isPresent() && hint.get().actionType() == AssistantActionType.DELETE_ATTRIBUTE) {
            return AssistantToolName.DELETE_ATTRIBUTE;
        }

        // Explicit relationship language determines the action family even
        // when one endpoint is unknown. That lets the relationship resolver
        // fail closed instead of accidentally reinterpreting the known class
        // as a class mutation.
        if (isRelationshipDelete(text)) {
            return AssistantToolName.DELETE_RELATIONSHIP;
        }
        if (isMultiplicity(text) && !mentions.isEmpty()) {
            return AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY;
        }
        AssistantToolName relationshipCreate = relationshipCreateTool(text);
        if (relationshipCreate != null && !mentions.isEmpty()) {
            return relationshipCreate;
        }

        if (mentions.size() == 1 && isClassRenamePhrase(text)) {
            return AssistantToolName.RENAME_CLASS;
        }

        if (mentions.size() == 1 && isAttributeAdditionPhrase(text)) {
            return AssistantToolName.ADD_ATTRIBUTES;
        }

        if (hint.isPresent()) {
            return switch (hint.get().actionType()) {
                case CREATE_CLASS -> mentions.isEmpty() ? AssistantToolName.CREATE_CLASS : null;
                case RENAME_CLASS -> AssistantToolName.RENAME_CLASS;
                case DELETE_CLASS -> AssistantToolName.DELETE_CLASS;
                case ADD_ATTRIBUTES -> AssistantToolName.ADD_ATTRIBUTES;
                case UPDATE_ATTRIBUTE -> isAttributeRename(text)
                        ? AssistantToolName.RENAME_ATTRIBUTE
                        : AssistantToolName.UPDATE_ATTRIBUTE_PROPERTIES;
                case DELETE_ATTRIBUTE -> AssistantToolName.DELETE_ATTRIBUTE;
                case CREATE_RELATIONSHIP -> {
                    AssistantToolName relationship = relationshipCreateTool(text);
                    yield relationship == null ? AssistantToolName.CREATE_ASSOCIATION : relationship;
                }
                case UPDATE_RELATIONSHIP -> isMultiplicity(text)
                        ? AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY
                        : AssistantToolName.CHANGE_RELATIONSHIP_TYPE;
                case DELETE_RELATIONSHIP -> AssistantToolName.DELETE_RELATIONSHIP;
            };
        }

        if (mentions.isEmpty() && isClassCreationPhrase(text)) {
            return AssistantToolName.CREATE_CLASS;
        }

        return null;
    }

    private List<AssistantToolName> compoundRoute(String userText) {
        Set<AssistantActionType> families = compoundDetector.requiredFamilies(userText);
        if (families.size() < 2) {
            return List.of();
        }

        String text = normalize(userText);
        List<AssistantToolName> result = new ArrayList<>();

        // Dependency order is deliberate: newly-created symbols must exist in
        // the ephemeral ProjectDocument before later steps reference them.
        if (families.contains(AssistantActionType.CREATE_CLASS)) {
            result.add(AssistantToolName.CREATE_CLASS);
        }
        if (families.contains(AssistantActionType.ADD_ATTRIBUTES)) {
            result.add(AssistantToolName.ADD_ATTRIBUTES);
        }
        if (families.contains(AssistantActionType.UPDATE_ATTRIBUTE)) {
            result.add(isAttributeRename(text)
                    ? AssistantToolName.RENAME_ATTRIBUTE
                    : AssistantToolName.UPDATE_ATTRIBUTE_PROPERTIES);
        }
        if (families.contains(AssistantActionType.DELETE_ATTRIBUTE)) {
            result.add(AssistantToolName.DELETE_ATTRIBUTE);
        }
        if (families.contains(AssistantActionType.CREATE_RELATIONSHIP)) {
            AssistantToolName relationship = relationshipCreateTool(text);
            result.add(relationship == null ? AssistantToolName.CREATE_ASSOCIATION : relationship);
        }
        if (families.contains(AssistantActionType.UPDATE_RELATIONSHIP)) {
            result.add(isMultiplicity(text)
                    ? AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY
                    : AssistantToolName.CHANGE_RELATIONSHIP_TYPE);
        }
        if (families.contains(AssistantActionType.DELETE_RELATIONSHIP)) {
            result.add(AssistantToolName.DELETE_RELATIONSHIP);
        }
        if (families.contains(AssistantActionType.RENAME_CLASS)) {
            result.add(AssistantToolName.RENAME_CLASS);
        }
        if (families.contains(AssistantActionType.DELETE_CLASS)) {
            result.add(AssistantToolName.DELETE_CLASS);
        }

        return uniqueExecutable(result);
    }

    private List<AssistantToolName> uniqueExecutable(List<AssistantToolName> tools) {
        LinkedHashSet<AssistantToolName> unique = new LinkedHashSet<>();
        if (tools != null) {
            for (AssistantToolName tool : tools) {
                if (tool != null && tool != AssistantToolName.ROUTE_REQUEST && tool != AssistantToolName.FINISH_PLAN) {
                    unique.add(tool);
                }
            }
        }
        return List.copyOf(unique);
    }

    private boolean isClassRenamePhrase(String text) {
        return containsAny(
                text,
                "debe llamarse",
                "ahora se llama",
                "cambia el nombre",
                "cambiale el nombre",
                "de nombre a",
                "renombra la clase",
                "renombrar la clase"
        );
    }

    private boolean isAttributeAdditionPhrase(String text) {
        if (containsAny(text, "campo", "atributo")
                && containsAny(text, "necesita", "agrega", "agregale", "anade", "anadele", "pon", "ponle", "crea")) {
            return true;
        }
        return ADD_TO_EXISTING_CLASS.matcher(text).find();
    }

    private boolean isAttributeRename(String text) {
        return containsAny(text, "renombra", "renonbra", "ahora se llama", "cambia")
                && !containsAny(text, "renombra la clase", "cambia el nombre de la clase");
    }

    private boolean isClassCreationPhrase(String text) {
        return containsAny(text, "crea", "crear", "nueva clase", "necesito una clase", "anade al modelo una nueva clase");
    }

    private boolean isRelationshipDelete(String text) {
        return containsAny(
                text,
                "desconecta",
                "desconectar",
                "quita el vinculo",
                "quita la relacion",
                "borra la relacion",
                "elimina la relacion",
                "ya no deben estar conect",
                "ya no debe estar conect"
        );
    }

    private boolean isMultiplicity(String text) {
        if (Pattern.compile("\\b[01]\\s*\\.\\.\\s*(?:\\*|[01])").matcher(text).find()) {
            return true;
        }
        boolean zero = containsAny(text, "cero", "ninguna", "ningun");
        boolean many = containsAny(text, "muchas", "muchos", "varias", "varios");
        return zero && many;
    }

    private AssistantToolName relationshipCreateTool(String text) {
        if (containsAny(text, "especializacion", "generalizacion", "hereda", "heredar")) {
            return AssistantToolName.CREATE_GENERALIZATION;
        }
        if (containsAny(text, "composicion", "compuesta", "compuesto")) {
            return AssistantToolName.CREATE_COMPOSITION;
        }
        if (containsAny(text, "agregacion", "agrupa", "agrupar")) {
            return AssistantToolName.CREATE_AGGREGATION;
        }
        if (containsAny(text, "asocia", "asociar", "conecta", "conectar", "relaciona", "relacionar")) {
            return AssistantToolName.CREATE_ASSOCIATION;
        }
        return null;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_*\\s.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
