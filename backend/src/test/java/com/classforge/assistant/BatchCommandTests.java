package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.application.ProjectCommandRejectedException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchCommandTests {

    private final ProjectCommandExecutor executor =
            new ProjectCommandExecutor();

    @Test
    void batchIsAppliedAsOneCompositeCommand() {
        UUID classId =
                UUID.randomUUID();

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

        ProjectDocument result =
                executor.execute(
                        ProjectDocument.empty(),
                        UmlCommandPayload.batch(
                                "Crear Animal",
                                List.of(create)
                        )
                );

        assertEquals(
                "Animal",
                result.umlModel()
                        .classes()
                        .getFirst()
                        .name()
        );
    }

    @Test
    void failedChildDoesNotMutateOriginal() {
        ProjectDocument original =
                ProjectDocument.empty();

        UUID classId =
                UUID.randomUUID();

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

        UmlCommandPayload bad =
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

        assertThrows(
                ProjectCommandRejectedException.class,
                () ->
                        executor.execute(
                                original,
                                UmlCommandPayload.batch(
                                        "Invalido",
                                        List.of(
                                                create,
                                                bad
                                        )
                                )
                        )
        );

        assertTrue(
                original.umlModel()
                        .classes()
                        .isEmpty()
        );
    }
}