package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssistantSemanticCompilerTests {

    private final AssistantEntityReferenceResolver resolver =
            new AssistantEntityReferenceResolver();

    private final AssistantSemanticCompiler compiler =
            new AssistantSemanticCompiler(
                    resolver
            );

    @Test
    void repairsMissingRelationshipEndpointsFromUserMentions() {
        ProjectDocument document =
                documentWith(
                        "Animal",
                        "Mascota"
                );

        AssistantSemanticPlan raw =
                new AssistantSemanticPlan(
                        "Crear asociacion",
                        List.of(
                                relationship(
                                        null,
                                        null
                                )
                        )
                );

        AssistantPlanAction compiled =
                compiler.compile(
                                "Crea una asociacion entre animal y mascota",
                                raw,
                                document
                        )
                        .actions()
                        .getFirst();

        assertEquals(
                "Animal",
                compiled.sourceClassName()
        );

        assertEquals(
                "Mascota",
                compiled.targetClassName()
        );
    }

    @Test
    void repairsTyposButDoesNotAutocorrectNewClassNames() {
        ProjectDocument document =
                documentWith(
                        "Animal",
                        "Mascota"
                );

        AssistantSemanticPlan relationshipRaw =
                new AssistantSemanticPlan(
                        "Crear asociacion",
                        List.of(
                                relationship(
                                        "4nimal",
                                        "msacota"
                                )
                        )
                );

        AssistantPlanAction relationship =
                compiler.compile(
                                "Crea una asociacion entre 4nimal y msacota",
                                relationshipRaw,
                                document
                        )
                        .actions()
                        .getFirst();

        assertEquals(
                "Animal",
                relationship.sourceClassName()
        );

        assertEquals(
                "Mascota",
                relationship.targetClassName()
        );

        AssistantPlanAction createClass =
                compiler.compile(
                                "Crea la clase 4nimal",
                                new AssistantSemanticPlan(
                                        "Crear clase",
                                        List.of(
                                                createClass(
                                                        "4nimal"
                                                )
                                        )
                                ),
                                document
                        )
                        .actions()
                        .getFirst();

        assertEquals(
                "4nimal",
                createClass.className()
        );

        assertNull(
                createClass.sourceClassName()
        );
    }

    private AssistantPlanAction relationship(
            String source,
            String target
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
                UmlRelationshipType.ASSOCIATION,
                null,
                1,
                null,
                1
        );
    }

    private AssistantPlanAction createClass(
            String name
    ) {
        return new AssistantPlanAction(
                AssistantActionType.CREATE_CLASS,
                name,
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
        );
    }

    private ProjectDocument documentWith(
            String... names
    ) {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        java.util.Arrays.stream(
                                        names
                                )
                                .map(
                                        name ->
                                                new UmlClass(
                                                        UUID.randomUUID(),
                                                        name,
                                                        List.of()
                                                )
                                )
                                .toList(),
                        List.of()
                ),
                DiagramLayout.empty()
        );
    }
}
