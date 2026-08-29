package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantPlanGroundingFilterTests {

    private final AssistantPlanGroundingFilter filter =
            new AssistantPlanGroundingFilter(
                    new AssistantEntityReferenceResolver()
            );

    @Test
    void relationshipRequestDropsHallucinatedAttributeUpdate() {
        AssistantSemanticPlan raw =
                new AssistantSemanticPlan(
                        "Relacionar Animal con Veterinario",
                        List.of(
                                relationship(
                                        "Animal",
                                        "Veterinario",
                                        UmlRelationshipType.ASSOCIATION
                                ),
                                updateAttribute(
                                        "Animal",
                                        "edad"
                                )
                        )
                );

        AssistantSemanticPlan sanitized =
                filter.sanitize(
                        "Conecta animal con veterinario",
                        raw,
                        documentWith(
                                "Animal",
                                "Veterinario"
                        )
                );

        assertEquals(
                1,
                sanitized.actions()
                        .size()
        );

        assertEquals(
                AssistantActionType.CREATE_RELATIONSHIP,
                sanitized.actions()
                        .getFirst()
                        .type()
        );
    }

    @Test
    void existingClassesHallucinatedByLlmAreDroppedButRelationshipsRemain() {
        AssistantSemanticPlan raw =
                new AssistantSemanticPlan(
                        "Crear relaciones",
                        List.of(
                                createClass(
                                        "Animal"
                                ),
                                createClass(
                                        "Mascota"
                                ),
                                relationship(
                                        "Veterinario",
                                        "Animal",
                                        UmlRelationshipType.ASSOCIATION
                                ),
                                relationship(
                                        "Animal",
                                        "Mascota",
                                        UmlRelationshipType.COMPOSITION
                                )
                        )
                );

        AssistantSemanticPlan sanitized =
                filter.sanitize(
                        "Crea una asociacion uno a uno entre Veterinario y Animal, "
                                + "y una composicion desde Animal hacia Mascota",
                        raw,
                        documentWith(
                                "Veterinario",
                                "Animal",
                                "Mascota"
                        )
                );

        assertEquals(
                2,
                sanitized.actions()
                        .size()
        );

        assertTrue(
                sanitized.actions()
                        .stream()
                        .allMatch(
                                action ->
                                        action.type()
                                                == AssistantActionType.CREATE_RELATIONSHIP
                        )
        );

        assertEquals(
                UmlRelationshipType.ASSOCIATION,
                sanitized.actions()
                        .get(0)
                        .relationshipType()
        );

        assertEquals(
                UmlRelationshipType.COMPOSITION,
                sanitized.actions()
                        .get(1)
                        .relationshipType()
        );
    }

    @Test
    void newClassMentionedByUserIsStillAllowed() {
        AssistantSemanticPlan raw =
                new AssistantSemanticPlan(
                        "Crear Cita",
                        List.of(
                                createClass(
                                        "Cita"
                                )
                        )
                );

        AssistantSemanticPlan sanitized =
                filter.sanitize(
                        "Crea una clase Cita",
                        raw,
                        documentWith(
                                "Animal"
                        )
                );

        assertEquals(
                1,
                sanitized.actions()
                        .size()
        );

        assertEquals(
                AssistantActionType.CREATE_CLASS,
                sanitized.actions()
                        .getFirst()
                        .type()
        );
    }

    @Test
    void classNamesSupportCommonSpanishPlural() {
        assertTrue(
                filter.mentionsEntity(
                        "Un Propietario tiene muchos animales",
                        "Animal"
                )
        );

        assertTrue(
                filter.mentionsEntity(
                        "Conecta veterinarios con animales",
                        "Veterinario"
                )
        );
    }

    @Test
    void createClassKeepsOnlyAttributesMentionedByUser() {
        AssistantSemanticPlan raw =
                new AssistantSemanticPlan(
                        "Crear Veterinario",
                        List.of(
                                createClassWith(
                                        "Veterinario",
                                        List.of(
                                                attribute(
                                                        "id",
                                                        UmlDataType.UUID
                                                ),
                                                attribute(
                                                        "nombre",
                                                        UmlDataType.STRING
                                                ),
                                                attribute(
                                                        "secreto",
                                                        UmlDataType.STRING
                                                )
                                        )
                                )
                        )
                );

        AssistantSemanticPlan sanitized =
                filter.sanitize(
                        "Crea Veterinario con id UUID y nombre",
                        raw,
                        ProjectDocument.empty()
                );

        assertEquals(
                2,
                sanitized.actions()
                        .getFirst()
                        .attributes()
                        .size()
        );
    }

    private ProjectDocument documentWith(
            String... classNames
    ) {
        List<UmlClass> classes =
                java.util.Arrays.stream(
                                classNames
                        )
                        .map(
                                name ->
                                        new UmlClass(
                                                UUID.randomUUID(),
                                                name,
                                                List.of()
                                        )
                        )
                        .toList();

        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        classes,
                        List.of()
                ),
                DiagramLayout.empty()
        );
    }

    private AssistantPlanAction relationship(
            String source,
            String target,
            UmlRelationshipType type
    ) {
        return new AssistantPlanAction(
                AssistantActionType.CREATE_RELATIONSHIP,
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
                type,
                null,
                null,
                null,
                null
        );
    }

    private AssistantPlanAction updateAttribute(
            String className,
            String attributeName
    ) {
        return new AssistantPlanAction(
                AssistantActionType.UPDATE_ATTRIBUTE,
                className,
                null,
                List.of(),
                attributeName,
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
    }

    private AssistantPlanAction createClass(
            String className
    ) {
        return createClassWith(
                className,
                List.of()
        );
    }

    private AssistantPlanAction createClassWith(
            String className,
            List<AssistantAttributePlan> attributes
    ) {
        return new AssistantPlanAction(
                AssistantActionType.CREATE_CLASS,
                className,
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
            UmlDataType type
    ) {
        return new AssistantAttributePlan(
                name,
                type,
                null,
                null,
                null,
                null,
                AssistantTypeSource.EXPLICIT
        );
    }
}