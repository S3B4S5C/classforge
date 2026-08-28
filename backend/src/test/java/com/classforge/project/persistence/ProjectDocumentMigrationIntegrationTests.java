package com.classforge.project.persistence;

import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-document-migration-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectDocumentMigrationIntegrationTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Test
    void readsEmptyCu01SnapshotAsProjectDocument() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();

        projectRepository.save(
                new ProjectEntity(
                        projectId,
                        ownerId,
                        "Legacy",
                        0L,
                        """
                        {"schemaVersion":"1.0","elements":[]}
                        """,
                        now,
                        now
                )
        );

        Project project = projectService.get(ownerId, projectId);

        assertEquals("1.0", project.document().schemaVersion());
        assertTrue(project.document().umlModel().classes().isEmpty());
        assertTrue(project.document().umlModel().relationships().isEmpty());
        assertTrue(project.document().layout().nodes().isEmpty());
    }
}