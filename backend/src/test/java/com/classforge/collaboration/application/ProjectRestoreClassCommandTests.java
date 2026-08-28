package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRestoreClassCommandTests {

    private final ProjectCommandExecutor executor =
            new ProjectCommandExecutor();

    @Test
    void restoreClassRecoversClassLayoutAndConnectedRelationshipsAtomically() {
        UUID animalId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();

        UmlClass appointment =
                new UmlClass(
                        appointmentId,
                        "Cita",
                        List.of()
                );

        ProjectDocument withoutAnimal =
                new ProjectDocument(
                        "1.0",
                        new UmlModel(
                                List.of(appointment),
                                List.of()
                        ),
                        new DiagramLayout(
                                Map.of(
                                        appointmentId,
                                        new DiagramNodeLayout(
                                                400,
                                                80,
                                                260,
                                                160
                                        )
                                )
                        )
                );

        UmlRelationship restoredRelationship =
                new UmlRelationship(
                        relationshipId,
                        animalId,
                        appointmentId,
                        UmlRelationshipType.ASSOCIATION,
                        new Multiplicity(
                                1,
                                1
                        ),
                        new Multiplicity(
                                0,
                                null
                        )
                );

        UmlCommandPayload restore =
                new UmlCommandPayload(
                        UUID.randomUUID(),
                        Instant.now(),
                        UmlCommandType.RESTORE_CLASS,
                        null,
                        null,
                        new UmlClass(
                                animalId,
                                "Animal",
                                List.of()
                        ),
                        new DiagramNodeLayout(
                                80,
                                80,
                                260,
                                160
                        ),
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                restoredRelationship
                        )
                );

        ProjectDocument restored =
                executor.execute(
                        withoutAnimal,
                        restore
                );

        assertTrue(
                restored.umlModel()
                        .classes()
                        .stream()
                        .anyMatch(
                                umlClass ->
                                        umlClass.id()
                                                .equals(animalId)
                        )
        );

        assertEquals(
                1,
                restored.umlModel()
                        .relationships()
                        .size()
        );

        assertEquals(
                relationshipId,
                restored.umlModel()
                        .relationships()
                        .getFirst()
                        .id()
        );

        assertTrue(
                restored.layout()
                        .nodes()
                        .containsKey(animalId)
        );
    }
}