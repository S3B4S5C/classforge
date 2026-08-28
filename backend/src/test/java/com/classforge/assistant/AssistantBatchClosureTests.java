package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistantBatchClosureTests {

    private final ProjectCommandExecutor executor =
            new ProjectCommandExecutor();

    @Test
    void oneAssistantBatchProducesOneProjectRevision() {
        UmlCommandPayload batch =
                UmlCommandPayload.batch(
                        "IA: prueba de cierre",
                        List.of(
                                createClass(
                                        "Animal",
                                        80
                                ),
                                createClass(
                                        "Veterinario",
                                        380
                                )
                        )
                );

        Project current =
                Project.create(
                        UUID.randomUUID(),
                        "Demo"
                );

        ProjectDocument next =
                executor.execute(
                        current.document(),
                        batch
                );

        Project persistedEquivalent =
                current.saveDocument(
                        next
                );

        assertEquals(
                2,
                next.umlModel()
                        .classes()
                        .size()
        );

        assertEquals(
                current.revision() + 1,
                persistedEquivalent.revision()
        );
    }

    private UmlCommandPayload createClass(
            String name,
            double x
    ) {
        return new UmlCommandPayload(
                UUID.randomUUID(),
                Instant.now(),
                UmlCommandType.CREATE_CLASS,
                null,
                null,
                new UmlClass(
                        UUID.randomUUID(),
                        name,
                        List.of()
                ),
                new DiagramNodeLayout(
                        x,
                        80,
                        260,
                        160
                ),
                null,
                null,
                null,
                null
        );
    }
}