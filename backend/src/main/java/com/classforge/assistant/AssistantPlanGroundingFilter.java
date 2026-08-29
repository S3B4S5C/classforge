package com.classforge.assistant;

import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AssistantPlanGroundingFilter {

    private final AssistantEntityReferenceResolver entityResolver;

    public AssistantPlanGroundingFilter(
            AssistantEntityReferenceResolver entityResolver
    ) {
        this.entityResolver =
                entityResolver;
    }

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9]+");

    private static final Pattern CAMEL_CASE =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public AssistantSemanticPlan sanitize(
            String userText,
            AssistantSemanticPlan plan,
            ProjectDocument document
    ) {
        if (
                plan == null
                        || plan.actions() == null
        ) {
            return plan;
        }

        Set<String> knownClassNames =
                new LinkedHashSet<>();

        if (
                document != null
                        && document.umlModel() != null
        ) {
            document.umlModel()
                    .classes()
                    .forEach(
                            umlClass ->
                                    knownClassNames.add(
                                            classKey(
                                                    umlClass.name()
                                            )
                                    )
                    );
        }

        List<String> redundantExistingCreates =
                new ArrayList<>();

        List<AssistantPlanAction> grounded =
                new ArrayList<>();

        for (
                AssistantPlanAction action
                : plan.actions()
        ) {
            AssistantPlanAction sanitized =
                    sanitizeAction(
                            userText,
                            action,
                            knownClassNames,
                            redundantExistingCreates
                    );

            if (sanitized != null) {
                grounded.add(
                        sanitized
                );

                if (
                        sanitized.type()
                                == AssistantActionType.CREATE_CLASS
                        && sanitized.className() != null
                ) {
                    knownClassNames.add(
                            classKey(
                                    sanitized.className()
                            )
                    );
                }
            }
        }

        if (
                !plan.actions().isEmpty()
                        && grounded.isEmpty()
        ) {
            if (!redundantExistingCreates.isEmpty()) {
                throw new AssistantPlanningException(
                        "La clase '"
                                + redundantExistingCreates.getFirst()
                                + "' ya existe en el modelo. "
                                + "El modelo local intento crearla nuevamente y el plan fue descartado."
                );
            }

            throw new AssistantPlanningException(
                    "El modelo local propuso cambios que no estan respaldados por tu mensaje. "
                            + "No se aplico ningun cambio."
            );
        }

        return new AssistantSemanticPlan(
                plan.summary(),
                List.copyOf(
                        grounded
                )
        );
    }

    private AssistantPlanAction sanitizeAction(
            String userText,
            AssistantPlanAction action,
            Set<String> knownClassNames,
            List<String> redundantExistingCreates
    ) {
        if (
                action == null
                        || action.type() == null
        ) {
            return null;
        }

        return switch (action.type()) {
            case CREATE_CLASS ->
                    sanitizeCreateClass(
                            userText,
                            action,
                            knownClassNames,
                            redundantExistingCreates
                    );

            case ADD_ATTRIBUTES ->
                    sanitizeAddAttributes(
                            userText,
                            action
                    );

            case RENAME_CLASS ->
                    mentionsExistingEntity(
                            userText,
                            action.className()
                    )
                            && mentionsEntity(
                            userText,
                            action.newName()
                    )
                            ? action
                            : null;

            case DELETE_CLASS ->
                    mentionsExistingEntity(
                            userText,
                            action.className()
                    )
                            ? action
                            : null;

            case UPDATE_ATTRIBUTE ->
                    mentionsExistingEntity(
                            userText,
                            action.className()
                    )
                            && mentionsExistingEntity(
                            userText,
                            action.attributeName()
                    )
                            && (
                            action.newAttributeName() == null
                                    || action.newAttributeName()
                                    .isBlank()
                                    || mentionsEntity(
                                    userText,
                                    action.newAttributeName()
                            )
                    )
                            ? action
                            : null;

            case DELETE_ATTRIBUTE ->
                    mentionsExistingEntity(
                            userText,
                            action.className()
                    )
                            && mentionsExistingEntity(
                            userText,
                            action.attributeName()
                    )
                            ? action
                            : null;

            case CREATE_RELATIONSHIP,
                 UPDATE_RELATIONSHIP,
                 DELETE_RELATIONSHIP ->
                    mentionsExistingEntity(
                            userText,
                            action.sourceClassName()
                    )
                            && mentionsExistingEntity(
                            userText,
                            action.targetClassName()
                    )
                            ? action
                            : null;
        };
    }

    private AssistantPlanAction sanitizeCreateClass(
            String userText,
            AssistantPlanAction action,
            Set<String> knownClassNames,
            List<String> redundantExistingCreates
    ) {
        if (
                !mentionsEntity(
                        userText,
                        action.className()
                )
        ) {
            return null;
        }

        String className =
                action.className();

        if (
                className != null
                        && knownClassNames.contains(
                        classKey(
                                className
                        )
                )
        ) {
            redundantExistingCreates.add(
                    className
            );

            return null;
        }

        List<AssistantAttributePlan> attributes =
                groundedAttributes(
                        userText,
                        action.safeAttributes()
                );

        return copyWithAttributes(
                action,
                attributes
        );
    }

    private AssistantPlanAction sanitizeAddAttributes(
            String userText,
            AssistantPlanAction action
    ) {
        if (
                !mentionsExistingEntity(
                        userText,
                        action.className()
                )
        ) {
            return null;
        }

        List<AssistantAttributePlan> attributes =
                groundedAttributes(
                        userText,
                        action.safeAttributes()
                );

        if (attributes.isEmpty()) {
            return null;
        }

        return copyWithAttributes(
                action,
                attributes
        );
    }

    private List<AssistantAttributePlan> groundedAttributes(
            String userText,
            List<AssistantAttributePlan> attributes
    ) {
        return attributes
                .stream()
                .filter(
                        attribute ->
                                attribute != null
                                        && mentionsEntity(
                                        userText,
                                        attribute.name()
                                )
                )
                .toList();
    }

    private AssistantPlanAction copyWithAttributes(
            AssistantPlanAction action,
            List<AssistantAttributePlan> attributes
    ) {
        return new AssistantPlanAction(
                action.type(),
                action.className(),
                action.newName(),
                attributes,
                action.attributeName(),
                action.newAttributeName(),
                action.dataType(),
                action.customTypeName(),
                action.visibility(),
                action.nullable(),
                action.identifier(),
                action.sourceClassName(),
                action.targetClassName(),
                action.relationshipType(),
                action.sourceLower(),
                action.sourceUpper(),
                action.targetLower(),
                action.targetUpper()
        );
    }

    private boolean mentionsExistingEntity(
            String userText,
            String entity
    ) {
        return mentionsEntity(
                userText,
                entity
        )
                || entityResolver.fuzzyMentions(
                userText,
                entity
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

        String compactEntity =
                normalize(
                        entity
                );

        if (
                normalizedText.isBlank()
                        || normalizedEntity.isBlank()
        ) {
            return false;
        }

        String paddedText =
                " "
                        + normalizedText
                        + " ";

        String paddedEntity =
                " "
                        + normalizedEntity
                        + " ";

        String paddedCompactEntity =
                " "
                        + compactEntity
                        + " ";

        if (
                paddedText.contains(
                        paddedEntity
                )
                        || paddedText.contains(
                        paddedCompactEntity
                )
        ) {
            return true;
        }

        List<String> textTokens =
                tokens(
                        normalizedText
                );

        List<String> entityTokens =
                tokens(
                        normalizedEntity
                );

        if (
                entityTokens.size() == 1
                        && "id".equals(
                        entityTokens.getFirst()
                )
                        && (
                        textTokens.contains(
                                "identificador"
                        )
                                || textTokens.contains(
                                "identifier"
                        )
                )
        ) {
            return true;
        }

        return entityTokens
                .stream()
                .allMatch(
                        entityToken ->
                                textTokens
                                        .stream()
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

    private String classKey(
            String value
    ) {
        return normalize(
                splitCamelCase(
                        value == null
                                ? ""
                                : value
                )
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
}