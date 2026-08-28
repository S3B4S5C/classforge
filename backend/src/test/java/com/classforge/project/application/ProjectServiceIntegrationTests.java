package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.UmlModelSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        assertEquals(UmlModelSnapshot.CURRENT_SCHEMA_VERSION, created.umlModel().schemaVersion());
        assertTrue(created.umlModel().elements().isEmpty());

        Project loaded = projectService.get(ownerId, created.id());

        assertEquals(created.id(), loaded.id());
        assertEquals(ownerId, loaded.ownerId());
    }

    @Test
    void isolatesProjectsBetweenOwners() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        Project project = projectService.create(ownerA, "Privado");

        assertFalse(projectService.list(ownerA).isEmpty());
        assertTrue(projectService.list(ownerB).isEmpty());
        assertThrows(ProjectNotFoundException.class, () -> projectService.get(ownerB, project.id()));
    }

    @Test
    void rejectsBlankProjectName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> projectService.create(UUID.randomUUID(), "   ")
        );
    }
}