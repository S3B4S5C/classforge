package com.classforge.assistant;

import com.classforge.project.domain.document.UmlDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantPlanNormalizerTests {

    private final AssistantPlanNormalizer normalizer =
            new AssistantPlanNormalizer();

    @Test
    void missingAttributeTypeDefaultsToString() {
        AssistantSemanticPlan plan =
                new AssistantSemanticPlan(
                        "Crear Veterinario",
                        List.of(
                                createClass(
                                        List.of(
                                                attribute(
                                                        "id",
                                                        UmlDataType.UUID,
                                                        AssistantTypeSource.EXPLICIT
                                                ),
                                                attribute(
                                                        "nombre",
                                                        null,
                                                        null
                                                )
                                        )
                                )
                        )
                );

        AssistantSemanticPlan normalized =
                normalizer.normalize(
                        plan
                );

        AssistantAttributePlan id =
                normalized.actions()
                        .getFirst()
                        .attributes()
                        .get(0);

        AssistantAttributePlan nombre =
                normalized.actions()
                        .getFirst()
                        .attributes()
                        .get(1);

        assertEquals(
                UmlDataType.UUID,
                id.dataType()
        );

        assertTrue(
                id.identifier()
        );

        assertFalse(
                id.nullable()
        );

        assertEquals(
                UmlDataType.STRING,
                nombre.dataType()
        );

        assertEquals(
                AssistantTypeSource.DEFAULT,
                nombre.typeSource()
        );
    }

    private AssistantPlanAction createClass(
            List<AssistantAttributePlan> attributes
    ) {
        return new AssistantPlanAction(
                AssistantActionType.CREATE_CLASS,
                "Veterinario",
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
    }

    private AssistantAttributePlan attribute(
            String name,
            UmlDataType dataType,
            AssistantTypeSource source
    ) {
        return new AssistantAttributePlan(
                name,
                dataType,
                null,
                null,
                null,
                null,
                source
        );
    }
}