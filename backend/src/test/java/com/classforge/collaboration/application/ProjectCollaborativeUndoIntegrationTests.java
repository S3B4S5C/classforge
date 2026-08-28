package com.classforge.collaboration.application;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.collaboration.protocol.ProjectOperationApplied;
import com.classforge.collaboration.protocol.ProjectOperationRequest;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.UmlClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu06-003-undo-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectCollaborativeUndoIntegrationTests {

    @Autowired
    private ProjectCollaborationService collaborationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void deleteThenRestoreBehavesAsNewCollaborativeRevisions() {
        UUID ownerId = UUID.randomUUID();

        userRepository.save(
                new UserEntity(
                        ownerId,
                        "Undo Owner",
                        "cu06-003-"
                                + ownerId
                                + "@classforge.test",
                        "unused",
                        Instant.now()
                )
        );

        Project project =
                projectService.create(
                        ownerId,
                        "Undo colaborativo"
                );

        UUID classId =
                UUID.randomUUID();

        ProjectOperationApplied created =
                collaborationService.apply(
                        ownerId,
                        project.id(),
                        operation(
                                project.id(),
                                0L,
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
                                )
                        )
                );

        assertEquals(
                1L,
                created.revision()
        );

        ProjectOperationApplied deleted =
                collaborationService.apply(
                        ownerId,
                        project.id(),
                        operation(
                                project.id(),
                                1L,
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
                                )
                        )
                );

        assertEquals(
                2L,
                deleted.revision()
        );

        ProjectOperationApplied restored =
                collaborationService.apply(
                        ownerId,
                        project.id(),
                        operation(
                                project.id(),
                                2L,
                                new UmlCommandPayload(
                                        UUID.randomUUID(),
                                        Instant.now(),
                                        UmlCommandType.RESTORE_CLASS,
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
                                        null,
                                        List.of()
                                )
                        )
                );

        assertEquals(
                3L,
                restored.revision()
        );

        Project reloaded =
                projectService.get(
                        ownerId,
                        project.id()
                );

        assertEquals(
                3L,
                reloaded.revision()
        );

        assertTrue(
                reloaded.document()
                        .umlModel()
                        .classes()
                        .stream()
                        .anyMatch(
                                umlClass ->
                                        umlClass.id()
                                                .equals(classId)
                        )
        );
    }

    private ProjectOperationRequest operation(
            UUID projectId,
            long revision,
            UmlCommandPayload command
    ) {
        return new ProjectOperationRequest(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                revision,
                command
        );
    }
}