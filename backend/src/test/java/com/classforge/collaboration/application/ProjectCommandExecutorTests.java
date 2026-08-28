package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectCommandExecutorTests {

    private final ProjectCommandExecutor executor =
            new ProjectCommandExecutor();

    @Test
    void createAndDeleteClassAreDeterministic() {
        UUID classId = UUID.randomUUID();

        UmlCommandPayload create =
                new UmlCommandPayload(
                        UUID.randomUUID(),
                        Instant.now(),
                        UmlCommandType.CREATE_CLASS,
                        null,
                        null,
                        new UmlClass(
                                classId,
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
                        null
                );

        ProjectDocument created =
                executor.execute(
                        ProjectDocument.empty(),
                        create
                );

        assertEquals(
                1,
                created.umlModel()
                        .classes()
                        .size()
        );

        assertTrue(
                created.layout()
                        .nodes()
                        .containsKey(classId)
        );

        UmlCommandPayload delete =
                new UmlCommandPayload(
                        UUID.randomUUID(),
                        Instant.now(),
                        UmlCommandType.DELETE_CLASS,
                        classId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        ProjectDocument deleted =
                executor.execute(
                        created,
                        delete
                );

        assertTrue(
                deleted.umlModel()
                        .classes()
                        .isEmpty()
        );

        assertFalse(
                deleted.layout()
                        .nodes()
                        .containsKey(classId)
        );
    }

    @Test
    void rejectsOperationAgainstMissingClass() {
        UmlCommandPayload command =
                new UmlCommandPayload(
                        UUID.randomUUID(),
                        Instant.now(),
                        UmlCommandType.RENAME_CLASS,
                        UUID.randomUUID(),
                        "Mascota",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        ProjectCommandRejectedException exception =
                assertThrows(
                        ProjectCommandRejectedException.class,
                        () ->
                                executor.execute(
                                        ProjectDocument.empty(),
                                        command
                                )
                );

        assertEquals(
                "CLASS_NOT_FOUND",
                exception.getCode()
        );
    }
}