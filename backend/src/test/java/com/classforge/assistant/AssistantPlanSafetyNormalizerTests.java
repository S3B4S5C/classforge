package com.classforge.assistant;

import com.classforge.project.domain.document.UmlDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistantPlanSafetyNormalizerTests {

    private final AssistantPlanNormalizer normalizer =
            new AssistantPlanNormalizer();

    @Test
    void inferredCustomWithoutNameFallsBackToString() {
        AssistantSemanticPlan normalized =
                normalizer.normalize(
                        new AssistantSemanticPlan(
                                "Crear Cliente",
                                List.of(
                                        createClassWith(
                                                new AssistantAttributePlan(
                                                        "nombre",
                                                        UmlDataType.CUSTOM,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.INFERRED
                                                )
                                        )
                                )
                        )
                );

        AssistantAttributePlan attribute =
                normalized.actions()
                        .getFirst()
                        .attributes()
                        .getFirst();

        assertEquals(
                UmlDataType.STRING,
                attribute.dataType()
        );

        assertEquals(
                AssistantTypeSource.DEFAULT,
                attribute.typeSource()
        );
    }

    @Test
    void explicitCustomWithoutNameIsRejected() {
        assertThrows(
                AssistantPlanningException.class,
                () ->
                        normalizer.normalize(
                                new AssistantSemanticPlan(
                                        "Crear Documento",
                                        List.of(
                                                createClassWith(
                                                        new AssistantAttributePlan(
                                                                "contenido",
                                                                UmlDataType.CUSTOM,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                AssistantTypeSource.EXPLICIT
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    @Test
    void scalarCustomUpdateWithoutNameIsRejected() {
        AssistantPlanAction update =
                new AssistantPlanAction(
                        AssistantActionType.UPDATE_ATTRIBUTE,
                        "Animal",
                        null,
                        List.of(),
                        "edad",
                        null,
                        UmlDataType.CUSTOM,
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

        assertThrows(
                AssistantPlanningException.class,
                () ->
                        normalizer.normalize(
                                new AssistantSemanticPlan(
                                        "Cambiar edad",
                                        List.of(
                                                update
                                        )
                                )
                        )
        );
    }

    private AssistantPlanAction createClassWith(
            AssistantAttributePlan attribute
    ) {
        return new AssistantPlanAction(
                AssistantActionType.CREATE_CLASS,
                "Cliente",
                null,
                List.of(
                        attribute
                ),
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
    }
}