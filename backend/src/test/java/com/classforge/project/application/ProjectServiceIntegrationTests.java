package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-project-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectServiceIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Test
    void createsAndPersistsAnEmptyProjectForItsOwner() {
        UUID ownerId = UUID.randomUUID();

        Project created = projectService.create(ownerId, "  Veterinaria  ");

        assertNotNull(created.id());
        assertEquals(ownerId, created.ownerId());
        assertEquals("Veterinaria", created.name());
        assertEquals(0L, created.revision());
        assertEquals(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                created.document().schemaVersion()
        );
        assertTrue(created.document().umlModel().classes().isEmpty());
        assertTrue(created.document().umlModel().relationships().isEmpty());
        assertTrue(created.document().layout().nodes().isEmpty());

        Project loaded = projectService.get(ownerId, created.id());

        assertEquals(created.id(), loaded.id());
        assertEquals(created.document(), loaded.document());
    }

    @Test
    void isolatesProjectsBetweenOwners() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        Project project = projectService.create(ownerA, "Privado");

        assertFalse(projectService.list(ownerA).isEmpty());
        assertTrue(projectService.list(ownerB).isEmpty());
        assertThrows(
                ProjectNotFoundException.class,
                () -> projectService.get(ownerB, project.id())
        );
    }

    @Test
    void renamingDoesNotChangeDocumentRevision() {
        UUID ownerId = UUID.randomUUID();
        Project created = projectService.create(ownerId, "Veterinaria");

        Project renamed = projectService.rename(
                ownerId,
                created.id(),
                "Veterinaria Central"
        );

        assertEquals("Veterinaria Central", renamed.name());
        assertEquals(0L, renamed.revision());
        assertEquals(created.document(), renamed.document());
    }

    @Test
    void savingDocumentIncrementsRevisionAndRejectsStaleBase() {
        UUID ownerId = UUID.randomUUID();
        Project created = projectService.create(ownerId, "Veterinaria");

        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(Map.of("id", "future-class")),
                        List.of()
                ),
                new DiagramLayout(
                        Map.of(
                                "future-class",
                                Map.of("x", 120, "y", 80)
                        )
                )
        );

        Project saved = projectService.saveDocument(
                ownerId,
                created.id(),
                0L,
                document
        );

        assertEquals(1L, saved.revision());
        assertEquals(document, saved.document());

        ProjectRevisionConflictException conflict = assertThrows(
                ProjectRevisionConflictException.class,
                () -> projectService.saveDocument(
                        ownerId,
                        created.id(),
                        0L,
                        document
                )
        );

        assertEquals(0L, conflict.getRequestedRevision());
        assertEquals(1L, conflict.getCurrentRevision());
    }

    @Test
    void anotherOwnerCannotSaveDocument() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        Project created = projectService.create(ownerA, "Privado");

        assertThrows(
                ProjectNotFoundException.class,
                () -> projectService.saveDocument(
                        ownerB,
                        created.id(),
                        0L,
                        ProjectDocument.empty()
                )
        );
    }

    @Test
    void rejectsBlankProjectName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> projectService.create(UUID.randomUUID(), "   ")
        );
    }
}