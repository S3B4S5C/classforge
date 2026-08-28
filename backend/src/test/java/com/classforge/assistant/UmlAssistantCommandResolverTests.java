package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UmlAssistantCommandResolverTests {

    private final AssistantPlanNormalizer normalizer =
            new AssistantPlanNormalizer();

    private final UmlAssistantCommandResolver resolver =
            new UmlAssistantCommandResolver(
                    new ProjectCommandExecutor(),
                    normalizer
            );

    @Test
    void richCreateClassIntentExpandsToCreatePlusAttributes() {
        AssistantSemanticPlan plan =
                new AssistantSemanticPlan(
                        "Crear Veterinario con id y nombre",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.CREATE_CLASS,
                                        "Veterinario",
                                        null,
                                        List.of(
                                                new AssistantAttributePlan(
                                                        "id",
                                                        UmlDataType.UUID,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.EXPLICIT
                                                ),
                                                new AssistantAttributePlan(
                                                        "nombre",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                )
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
                                )
                        )
                );

        UmlCommandPayload batch =
                resolver.resolve(
                        plan,
                        ProjectDocument.empty()
                );

        assertEquals(
                UmlCommandType.BATCH,
                batch.type()
        );

        assertEquals(
                3,
                batch.safeCommands()
                        .size()
        );

        assertEquals(
                UmlCommandType.CREATE_CLASS,
                batch.safeCommands()
                        .get(0)
                        .type()
        );

        assertEquals(
                UmlCommandType.ADD_ATTRIBUTE,
                batch.safeCommands()
                        .get(1)
                        .type()
        );

        assertEquals(
                UmlCommandType.ADD_ATTRIBUTE,
                batch.safeCommands()
                        .get(2)
                        .type()
        );

        ProjectDocument preview =
                resolver.preview(
                        ProjectDocument.empty(),
                        batch
                );

        assertEquals(
                2,
                preview.umlModel()
                        .classes()
                        .getFirst()
                        .attributes()
                        .size()
        );

        assertEquals(
                UmlDataType.STRING,
                preview.umlModel()
                        .classes()
                        .getFirst()
                        .attributes()
                        .get(1)
                        .dataType()
        );
    }

    @Test
    void addAttributesIntentExpandsToMultipleCommands() {
        AssistantSemanticPlan createPlan =
                new AssistantSemanticPlan(
                        "Crear Veterinario",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.CREATE_CLASS,
                                        "Veterinario",
                                        null,
                                        List.of(),
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
                                )
                        )
                );

        UmlCommandPayload createBatch =
                resolver.resolve(
                        createPlan,
                        ProjectDocument.empty()
                );

        ProjectDocument current =
                resolver.preview(
                        ProjectDocument.empty(),
                        createBatch
                );

        AssistantSemanticPlan addPlan =
                new AssistantSemanticPlan(
                        "Agregar telefono y correo",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.ADD_ATTRIBUTES,
                                        "Veterinario",
                                        null,
                                        List.of(
                                                new AssistantAttributePlan(
                                                        "telefono",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                ),
                                                new AssistantAttributePlan(
                                                        "correo",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                )
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
                                )
                        )
                );

        UmlCommandPayload addBatch =
                resolver.resolve(
                        addPlan,
                        current
                );

        assertEquals(
                2,
                addBatch.safeCommands()
                        .size()
        );
    }
}