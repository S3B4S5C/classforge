package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.validation.ProjectDocumentValidationReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-explicit-validation-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectExplicitValidationIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Test
    void validatesDraftWithoutPersistingOrChangingRevision() {
        UUID ownerId = UUID.randomUUID();

        Project project =
                projectService.create(
                        ownerId,
                        "Veterinaria"
                );

        ProjectDocument draft =
                new ProjectDocument(
                        "1.0",
                        new UmlModel(
                                List.of(
                                        new UmlClass(
                                                UUID.randomUUID(),
                                                "Animal",
                                                List.of()
                                        )
                                ),
                                List.of()
                        ),
                        DiagramLayout.empty()
                );

        ProjectDocumentValidationReport report =
                projectService.validateDocument(
                        ownerId,
                        project.id(),
                        draft
                );

        assertTrue(report.valid());
        assertTrue(report.warnings() > 0);

        Project reloaded =
                projectService.get(
                        ownerId,
                        project.id()
                );

        assertEquals(0L, reloaded.revision());
        assertTrue(
                reloaded.document()
                        .umlModel()
                        .classes()
                        .isEmpty()
        );
    }

    @Test
    void validationStillEnforcesOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        Project project =
                projectService.create(
                        ownerId,
                        "Privado"
                );

        assertThrows(
                ProjectNotFoundException.class,
                () ->
                        projectService.validateDocument(
                                strangerId,
                                project.id(),
                                ProjectDocument.empty()
                        )
        );
    }
}