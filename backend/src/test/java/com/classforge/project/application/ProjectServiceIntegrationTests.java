package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlVisibility;
import com.classforge.project.validation.ProjectDocumentValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    void persistsTypedUmlClassAndAttribute() {
        UUID ownerId = UUID.randomUUID();
        Project created = projectService.create(ownerId, "Veterinaria");
        UUID classId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        classId,
                                        "Animal",
                                        List.of(
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "id",
                                                        UmlDataType.LONG,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        false,
                                                        true
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                new DiagramLayout(
                        Map.of(classId, DiagramNodeLayout.defaultForIndex(0))
                )
        );

        Project saved = projectService.saveDocument(
                ownerId,
                created.id(),
                0,
                document
        );

        assertEquals(1L, saved.revision());

        Project loaded = projectService.get(ownerId, created.id());
        assertEquals(
                "Animal",
                loaded.document().umlModel().classes().getFirst().name()
        );
        assertEquals(
                "id",
                loaded.document()
                        .umlModel()
                        .classes()
                        .getFirst()
                        .attributes()
                        .getFirst()
                        .name()
        );
    }

    @Test
    void rejectsInvalidDocumentBeforePersisting() {
        UUID ownerId = UUID.randomUUID();
        Project created = projectService.create(ownerId, "Veterinaria");

        ProjectDocument invalid = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        UUID.randomUUID(),
                                        "Animal con espacios",
                                        List.of()
                                )
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        assertThrows(
                ProjectDocumentValidationException.class,
                () -> projectService.saveDocument(
                        ownerId,
                        created.id(),
                        0,
                        invalid
                )
        );

        assertEquals(
                0L,
                projectService.get(ownerId, created.id()).revision()
        );
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
    }

    @Test
    void savingDocumentIncrementsRevisionAndRejectsStaleBase() {
        UUID ownerId = UUID.randomUUID();
        Project created = projectService.create(ownerId, "Veterinaria");

        Project saved = projectService.saveDocument(
                ownerId,
                created.id(),
                0L,
                ProjectDocument.empty()
        );

        assertEquals(1L, saved.revision());

        ProjectRevisionConflictException conflict = assertThrows(
                ProjectRevisionConflictException.class,
                () -> projectService.saveDocument(
                        ownerId,
                        created.id(),
                        0L,
                        ProjectDocument.empty()
                )
        );

        assertEquals(1L, conflict.getCurrentRevision());
    }
}