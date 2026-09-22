package com.classforge.assistant;

import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlVisibility;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantPlanNormalizer {

    public AssistantSemanticPlan normalize(
            AssistantSemanticPlan plan
    ) {
        if (
                plan == null
                        || plan.summary() == null
                        || plan.summary().isBlank()
                        || plan.actions() == null
                        || plan.actions().isEmpty()
        ) {
            throw new AssistantPlanningException(
                    "El modelo local devolvio un plan vacio"
            );
        }

        if (plan.actions().size() > 30) {
            throw new AssistantPlanningException(
                    "El plan supera el maximo de 30 intenciones"
            );
        }

        return new AssistantSemanticPlan(
                plan.summary().trim(),
                plan.actions()
                        .stream()
                        .map(
                                this::normalizeAction
                        )
                        .toList()
        );
    }

    private AssistantPlanAction normalizeAction(
            AssistantPlanAction action
    ) {
        if (
                action == null
                        || action.type() == null
        ) {
            throw new AssistantPlanningException(
                    "Una intencion no contiene type"
            );
        }

        List<AssistantAttributePlan> attributes =
                switch (action.type()) {
                    case CREATE_CLASS,
                         CREATE_ASSOCIATION_CLASS,
                         ADD_ATTRIBUTES ->
                            action.safeAttributes()
                                    .stream()
                                    .map(
                                            this::normalizeAttribute
                                    )
                                    .toList();

                    default ->
                            action.safeAttributes();
                };

        String customTypeName =
                trimOrNull(
                        action.customTypeName()
                );

        if (
                action.dataType()
                        == UmlDataType.CUSTOM
                        && customTypeName == null
        ) {
            throw new AssistantPlanningException(
                    "El modelo local propuso CUSTOM sin indicar el nombre del tipo personalizado "
                            + "para el atributo '"
                            + (
                            action.attributeName() == null
                                    ? "?"
                                    : action.attributeName()
                    )
                            + "'. No se aplico ningun cambio."
            );
        }

        if (
                action.dataType()
                        != UmlDataType.CUSTOM
        ) {
            customTypeName =
                    null;
        }

        return new AssistantPlanAction(
                action.type(),
                trimOrNull(
                        action.className()
                ),
                trimOrNull(
                        action.newName()
                ),
                attributes,
                trimOrNull(
                        action.attributeName()
                ),
                trimOrNull(
                        action.newAttributeName()
                ),
                action.dataType(),
                customTypeName,
                action.visibility(),
                action.nullable(),
                action.identifier(),
                trimOrNull(
                        action.sourceClassName()
                ),
                trimOrNull(
                        action.targetClassName()
                ),
                action.relationshipType(),
                action.sourceLower(),
                action.sourceUpper(),
                action.targetLower(),
                action.targetUpper(),
                action.relationshipId()
        );
    }

    private AssistantAttributePlan normalizeAttribute(
            AssistantAttributePlan attribute
    ) {
        if (
                attribute == null
                        || attribute.name() == null
                        || attribute.name().isBlank()
        ) {
            throw new AssistantPlanningException(
                    "Un atributo propuesto no contiene nombre"
            );
        }

        boolean idByConvention =
                "id".equalsIgnoreCase(
                        attribute.name()
                                .trim()
                );

        boolean identifier =
                attribute.identifier() == null
                        ? idByConvention
                        : attribute.identifier();

        boolean nullable =
                attribute.nullable() == null
                        ? !identifier
                        : attribute.nullable();

        UmlDataType dataType =
                attribute.dataType() == null
                        ? UmlDataType.STRING
                        : attribute.dataType();

        AssistantTypeSource typeSource =
                attribute.typeSource() == null
                        ? (
                        attribute.dataType() == null
                                ? AssistantTypeSource.DEFAULT
                                : AssistantTypeSource.INFERRED
                )
                        : attribute.typeSource();

        String customTypeName =
                trimOrNull(
                        attribute.customTypeName()
                );

        if (
                dataType
                        == UmlDataType.CUSTOM
                        && customTypeName == null
        ) {
            if (
                    typeSource
                            == AssistantTypeSource.EXPLICIT
            ) {
                throw new AssistantPlanningException(
                        "El atributo '"
                                + attribute.name()
                                + "' usa CUSTOM pero no indica el nombre del tipo personalizado. "
                                + "No se aplico ningun cambio."
                );
            }

            dataType =
                    UmlDataType.STRING;

            typeSource =
                    AssistantTypeSource.DEFAULT;
        }

        if (
                dataType
                        != UmlDataType.CUSTOM
        ) {
            customTypeName =
                    null;
        }

        return new AssistantAttributePlan(
                attribute.name()
                        .trim(),
                dataType,
                customTypeName,
                attribute.visibility() == null
                        ? UmlVisibility.PRIVATE
                        : attribute.visibility(),
                nullable,
                identifier,
                typeSource
        );
    }

    private String trimOrNull(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }
}