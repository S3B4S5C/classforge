package com.classforge.assistant;

import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class AssistantSemanticCompiler {

    private final AssistantEntityReferenceResolver entityResolver;

    public AssistantSemanticCompiler(
            AssistantEntityReferenceResolver entityResolver
    ) {
        this.entityResolver =
                entityResolver;
    }

    public AssistantSemanticPlan compile(
            String userText,
            AssistantSemanticPlan rawPlan,
            ProjectDocument document
    ) {
        if (
                rawPlan == null
                        || rawPlan.actions() == null
        ) {
            return rawPlan;
        }

        List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions =
                entityResolver.resolveMentions(
                        userText,
                        document
                );

        List<AssistantPlanAction> compiled =
                rawPlan.actions()
                        .stream()
                        .map(
                                action ->
                                        compileAction(
                                                action,
                                                mentions,
                                                document
                                        )
                        )
                        .toList();

        return new AssistantSemanticPlan(
                rawPlan.summary(),
                compiled
        );
    }

    private AssistantPlanAction compileAction(
            AssistantPlanAction action,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions,
            ProjectDocument document
    ) {
        if (
                action == null
                        || action.type() == null
        ) {
            return action;
        }

        return switch (action.type()) {
            case CREATE_CLASS ->
                    action;

            case RENAME_CLASS,
                 DELETE_CLASS,
                 ADD_ATTRIBUTES ->
                    copy(
                            action,
                            canonicalExistingClass(
                                    action.className(),
                                    mentions,
                                    document,
                                    List.of()
                            ),
                            action.attributeName(),
                            action.sourceClassName(),
                            action.targetClassName()
                    );

            case UPDATE_ATTRIBUTE,
                 DELETE_ATTRIBUTE ->
                    compileAttributeAction(
                            action,
                            mentions,
                            document
                    );

            case CREATE_RELATIONSHIP,
                 UPDATE_RELATIONSHIP,
                 DELETE_RELATIONSHIP ->
                    compileRelationship(
                            action,
                            mentions,
                            document
                    );
        };
    }

    private AssistantPlanAction compileRelationship(
            AssistantPlanAction action,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions,
            ProjectDocument document
    ) {
        String source =
                canonicalFromRaw(
                        action.sourceClassName(),
                        document
                )
                        .orElse(
                                null
                        );

        String target =
                canonicalFromRaw(
                        action.targetClassName(),
                        document
                )
                        .orElse(
                                null
                        );

        if (
                source == null
                        && target == null
                        && mentions.size() == 2
        ) {
            source =
                    mentions.get(0)
                            .canonicalName();

            target =
                    mentions.get(1)
                            .canonicalName();
        } else if (
                source == null
                        && target != null
        ) {
            source =
                    uniqueMentionExcluding(
                            mentions,
                            target
                    );
        } else if (
                target == null
                        && source != null
        ) {
            target =
                    uniqueMentionExcluding(
                            mentions,
                            source
                    );
        }

        return copy(
                action,
                action.className(),
                action.attributeName(),
                source,
                target
        );
    }

    private AssistantPlanAction compileAttributeAction(
            AssistantPlanAction action,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions,
            ProjectDocument document
    ) {
        String className =
                canonicalExistingClass(
                        action.className(),
                        mentions,
                        document,
                        List.of()
                );

        String attributeName =
                entityResolver.resolveExistingAttribute(
                                className,
                                action.attributeName(),
                                document
                        )
                        .map(
                                AssistantEntityReferenceResolver.ResolvedAttributeReference::canonicalName
                        )
                        .orElse(
                                action.attributeName()
                        );

        return copy(
                action,
                className,
                attributeName,
                action.sourceClassName(),
                action.targetClassName()
        );
    }

    private String canonicalExistingClass(
            String raw,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions,
            ProjectDocument document,
            List<String> excluded
    ) {
        Optional<String> canonical =
                canonicalFromRaw(
                        raw,
                        document
                );

        if (
                canonical.isPresent()
                        && !excluded.contains(
                        canonical.get()
                )
        ) {
            return canonical.get();
        }

        List<String> candidates =
                mentions.stream()
                        .map(
                                AssistantEntityReferenceResolver.ResolvedClassReference::canonicalName
                        )
                        .filter(
                                name ->
                                        !excluded.contains(
                                                name
                                        )
                        )
                        .distinct()
                        .toList();

        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        return raw;
    }

    private Optional<String> canonicalFromRaw(
            String raw,
            ProjectDocument document
    ) {
        return entityResolver.resolveExistingClass(
                        raw,
                        document
                )
                .map(
                        AssistantEntityReferenceResolver.ResolvedClassReference::canonicalName
                );
    }

    private String uniqueMentionExcluding(
            List<AssistantEntityReferenceResolver.ResolvedClassReference> mentions,
            String excluded
    ) {
        List<String> candidates =
                mentions.stream()
                        .map(
                                AssistantEntityReferenceResolver.ResolvedClassReference::canonicalName
                        )
                        .filter(
                                name ->
                                        excluded == null
                                                || !excluded.equals(
                                                name
                                        )
                        )
                        .distinct()
                        .toList();

        return candidates.size() == 1
                ? candidates.getFirst()
                : null;
    }

    private AssistantPlanAction copy(
            AssistantPlanAction action,
            String className,
            String attributeName,
            String sourceClassName,
            String targetClassName
    ) {
        return new AssistantPlanAction(
                action.type(),
                className,
                action.newName(),
                action.safeAttributes(),
                attributeName,
                action.newAttributeName(),
                action.dataType(),
                action.customTypeName(),
                action.visibility(),
                action.nullable(),
                action.identifier(),
                sourceClassName,
                targetClassName,
                action.relationshipType(),
                action.sourceLower(),
                action.sourceUpper(),
                action.targetLower(),
                action.targetUpper()
        );
    }
}
