package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantSemanticPipelineHardeningTests {

    private final AssistantEntityReferenceResolver entityResolver =
            new AssistantEntityReferenceResolver();

    @Test
    void groundingAcceptsCamelCaseNamesThatAreLiterallyInThePrompt() {
        AssistantPlanGroundingFilter grounding =
                new AssistantPlanGroundingFilter(entityResolver);

        assertTrue(
                grounding.mentionsEntity(
                        "Crea una clase HistorialClinico",
                        "HistorialClinico"
                )
        );

        assertTrue(
                grounding.mentionsEntity(
                        "Renombra Veterinario a MedicoVeterinario",
                        "MedicoVeterinario"
                )
        );

        assertTrue(
                grounding.mentionsEntity(
                        "En Mascota renombra peso a pesoKg",
                        "pesoKg"
                )
        );
    }

    @Test
    void semanticCompilerCanonicalizesExistingAttributeTypos() {
        UUID mascotaId = UUID.randomUUID();
        UUID pesoId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        mascotaId,
                                        "Mascota",
                                        List.of(
                                                new UmlAttribute(
                                                        pesoId,
                                                        "peso",
                                                        UmlDataType.DECIMAL,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        false
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        AssistantPlanAction rawAction = new AssistantPlanAction(
                AssistantActionType.UPDATE_ATTRIBUTE,
                "Masctoa",
                null,
                List.of(),
                "pseo",
                "pesoKg",
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

        AssistantPlanAction compiled =
                new AssistantSemanticCompiler(entityResolver)
                        .compile(
                                "En Masctoa cambia pseo por pesoKg",
                                new AssistantSemanticPlan(
                                        "Renombrar peso",
                                        List.of(rawAction)
                                ),
                                document
                        )
                        .actions()
                        .getFirst();

        assertEquals("Mascota", compiled.className());
        assertEquals("peso", compiled.attributeName());
        assertEquals("pesoKg", compiled.newAttributeName());
    }

    @Test
    void scopedAttributeResolutionStillRejectsAmbiguousShortTypos() {
        UUID mascotaId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        mascotaId,
                                        "Mascota",
                                        List.of(
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "peso",
                                                        UmlDataType.DECIMAL,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        false
                                                ),
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "beso",
                                                        UmlDataType.STRING,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        false
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        assertTrue(
                entityResolver.resolveExistingAttribute(
                                "Mascota",
                                "ceso",
                                document
                        )
                        .isEmpty()
        );
    }

    @Test
    void updateRelationshipPreservesMissingSideAndTreatsBareStarAsZeroToMany() {
        UUID propietarioId = UUID.randomUUID();
        UUID mascotaId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        List.of(
                                new UmlClass(propietarioId, "Propietario", List.of()),
                                new UmlClass(mascotaId, "Mascota", List.of())
                        ),
                        List.of(
                                new UmlRelationship(
                                        relationshipId,
                                        propietarioId,
                                        mascotaId,
                                        UmlRelationshipType.ASSOCIATION,
                                        Multiplicity.one(),
                                        Multiplicity.one()
                                )
                        )
                ),
                DiagramLayout.empty()
        );

        AssistantPlanAction update = new AssistantPlanAction(
                AssistantActionType.UPDATE_RELATIONSHIP,
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
                "Propietario",
                "Mascota",
                null,
                null,
                null,
                null,
                -1
        );

        UmlAssistantCommandResolver resolver =
                new UmlAssistantCommandResolver(
                        new ProjectCommandExecutor(),
                        new AssistantPlanNormalizer()
                );

        UmlCommandPayload batch = resolver.resolve(
                new AssistantSemanticPlan(
                        "Mascota pasa a 0..*",
                        List.of(update)
                ),
                document
        );

        UmlCommandPayload command = batch.safeCommands()
                .stream()
                .filter(candidate -> candidate.type() == UmlCommandType.UPDATE_RELATIONSHIP)
                .findFirst()
                .orElseThrow();

        assertNotNull(command.relationship());
        assertEquals(Multiplicity.one(), command.relationship().sourceMultiplicity());
        assertEquals(0, command.relationship().targetMultiplicity().lower());
        assertEquals(null, command.relationship().targetMultiplicity().upper());
    }
}
