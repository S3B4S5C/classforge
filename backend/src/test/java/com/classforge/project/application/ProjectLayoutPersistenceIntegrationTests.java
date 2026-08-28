package com.classforge.project.application;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-layout-persistence-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectLayoutPersistenceIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Test
    void movingAClassPersistsLayoutWithoutChangingUmlSemantics() {
        UUID ownerId = UUID.randomUUID();

        Project created =
                projectService.create(
                        ownerId,
                        "Veterinaria"
                );

        UUID classId = UUID.randomUUID();

        UmlClass animal = new UmlClass(
                classId,
                "Animal",
                List.of()
        );

        ProjectDocument firstDocument =
                new ProjectDocument(
                        "1.0",
                        new UmlModel(
                                List.of(animal),
                                List.of()
                        ),
                        new DiagramLayout(
                                Map.of(
                                        classId,
                                        new DiagramNodeLayout(
                                                80,
                                                80,
                                                260,
                                                160
                                        )
                                )
                        )
                );

        Project revisionOne =
                projectService.saveDocument(
                        ownerId,
                        created.id(),
                        0,
                        firstDocument
                );

        ProjectDocument movedDocument =
                new ProjectDocument(
                        revisionOne.document().schemaVersion(),
                        revisionOne.document().umlModel(),
                        new DiagramLayout(
                                Map.of(
                                        classId,
                                        new DiagramNodeLayout(
                                                420,
                                                260,
                                                260,
                                                160
                                        )
                                )
                        )
                );

        Project revisionTwo =
                projectService.saveDocument(
                        ownerId,
                        created.id(),
                        1,
                        movedDocument
                );

        Project loaded =
                projectService.get(
                        ownerId,
                        created.id()
                );

        assertEquals(
                2,
                revisionTwo.revision()
        );

        assertEquals(
                "Animal",
                loaded.document()
                        .umlModel()
                        .classes()
                        .getFirst()
                        .name()
        );

        assertEquals(
                420,
                loaded.document()
                        .layout()
                        .nodes()
                        .get(classId)
                        .x()
        );

        assertEquals(
                260,
                loaded.document()
                        .layout()
                        .nodes()
                        .get(classId)
                        .y()
        );
    }
}