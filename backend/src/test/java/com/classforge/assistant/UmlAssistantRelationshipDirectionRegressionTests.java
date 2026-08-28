package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UmlAssistantRelationshipDirectionRegressionTests {

    private final UmlAssistantCommandResolver resolver =
            new UmlAssistantCommandResolver(
                    new ProjectCommandExecutor(),
                    new AssistantPlanNormalizer()
            );

    @Test
    void reverseAssociationCanBeUpdatedAndUsesRequestedDirection() {
        UmlClass animal =
                umlClass(
                        "Animal"
                );

        UmlClass veterinario =
                umlClass(
                        "Veterinario"
                );

        UmlRelationship existing =
                association(
                        animal,
                        veterinario
                );

        ProjectDocument document =
                document(
                        List.of(
                                animal,
                                veterinario
                        ),
                        List.of(
                                existing
                        )
                );

        AssistantPlanAction action =
                relationshipAction(
                        AssistantActionType.UPDATE_RELATIONSHIP,
                        "Veterinario",
                        "Animal",
                        UmlRelationshipType.COMPOSITION
                );

        UmlCommandPayload batch =
                resolver.resolve(
                        new AssistantSemanticPlan(
                                "Cambiar relacion a composicion",
                                List.of(
                                        action
                                )
                        ),
                        document
                );

        ProjectDocument preview =
                resolver.preview(
                        document,
                        batch
                );

        assertEquals(
                1,
                preview.umlModel()
                        .relationships()
                        .size()
        );

        UmlRelationship updated =
                preview.umlModel()
                        .relationships()
                        .getFirst();

        assertEquals(
                existing.id(),
                updated.id()
        );

        assertEquals(
                UmlRelationshipType.COMPOSITION,
                updated.type()
        );

        assertEquals(
                veterinario.id(),
                updated.sourceClassId()
        );

        assertEquals(
                animal.id(),
                updated.targetClassId()
        );
    }

    @Test
    void reverseAssociationCanBeDeletedBySamePairOfClasses() {
        UmlClass animal =
                umlClass(
                        "Animal"
                );

        UmlClass veterinario =
                umlClass(
                        "Veterinario"
                );

        ProjectDocument document =
                document(
                        List.of(
                                animal,
                                veterinario
                        ),
                        List.of(
                                association(
                                        animal,
                                        veterinario
                                )
                        )
                );

        AssistantPlanAction action =
                relationshipAction(
                        AssistantActionType.DELETE_RELATIONSHIP,
                        "Veterinario",
                        "Animal",
                        null
                );

        UmlCommandPayload batch =
                resolver.resolve(
                        new AssistantSemanticPlan(
                                "Eliminar relacion",
                                List.of(
                                        action
                                )
                        ),
                        document
                );

        ProjectDocument preview =
                resolver.preview(
                        document,
                        batch
                );

        assertTrue(
                preview.umlModel()
                        .relationships()
                        .isEmpty()
        );
    }

    @Test
    void reverseDirectedRelationshipRequiresRealDirection() {
        UmlClass animal =
                umlClass(
                        "Animal"
                );

        UmlClass veterinario =
                umlClass(
                        "Veterinario"
                );

        UmlRelationship composition =
                new UmlRelationship(
                        UUID.randomUUID(),
                        animal.id(),
                        veterinario.id(),
                        UmlRelationshipType.COMPOSITION,
                        new Multiplicity(
                                1,
                                1
                        ),
                        new Multiplicity(
                                1,
                                1
                        )
                );

        ProjectDocument document =
                document(
                        List.of(
                                animal,
                                veterinario
                        ),
                        List.of(
                                composition
                        )
                );

        AssistantPlanAction action =
                relationshipAction(
                        AssistantActionType.DELETE_RELATIONSHIP,
                        "Veterinario",
                        "Animal",
                        null
                );

        AssistantPlanningException exception =
                assertThrows(
                        AssistantPlanningException.class,
                        () ->
                                resolver.resolve(
                                        new AssistantSemanticPlan(
                                                "Eliminar relacion",
                                                List.of(
                                                        action
                                                )
                                        ),
                                        document
                                )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "tiene direccion UML"
                        )
        );
    }

    private UmlClass umlClass(
            String name
    ) {
        return new UmlClass(
                UUID.randomUUID(),
                name,
                List.of()
        );
    }

    private UmlRelationship association(
            UmlClass source,
            UmlClass target
    ) {
        return new UmlRelationship(
                UUID.randomUUID(),
                source.id(),
                target.id(),
                UmlRelationshipType.ASSOCIATION,
                new Multiplicity(
                        1,
                        1
                ),
                new Multiplicity(
                        1,
                        1
                )
        );
    }

    private ProjectDocument document(
            List<UmlClass> classes,
            List<UmlRelationship> relationships
    ) {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        classes,
                        relationships
                ),
                DiagramLayout.empty()
        );
    }

    private AssistantPlanAction relationshipAction(
            AssistantActionType type,
            String source,
            String target,
            UmlRelationshipType relationshipType
    ) {
        return new AssistantPlanAction(
                type,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                source,
                target,
                relationshipType,
                null,
                null,
                null,
                null
        );
    }
}