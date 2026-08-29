package com.classforge.project.access;

import com.classforge.project.application.AccessibleProject;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-membership-access-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProjectMembershipAccessIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Test
    void editorCanReadListValidateAndSaveButCannotRename() {
        UUID ownerId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        Project project = projectService.create(ownerId, "Veterinaria");

        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        Project loadedByEditor =
                projectService.get(editorId, project.id());

        assertEquals(project.id(), loadedByEditor.id());

        List<AccessibleProject> editorProjects =
                projectService.listAccessible(editorId);

        assertEquals(1, editorProjects.size());
        assertEquals(
                ProjectAccessRole.EDITOR,
                editorProjects.getFirst().accessRole()
        );

        projectService.validateDocument(
                editorId,
                project.id(),
                ProjectDocument.empty()
        );

        Project saved = projectService.saveDocument(
                editorId,
                project.id(),
                0L,
                ProjectDocument.empty()
        );

        assertEquals(1L, saved.revision());

        assertThrows(
                ProjectNotFoundException.class,
                () -> projectService.rename(
                        editorId,
                        project.id(),
                        "No permitido"
                )
        );

        assertThrows(
                ProjectNotFoundException.class,
                () -> projectService.get(
                        strangerId,
                        project.id()
                )
        );
    }

    @Test
    void ownerKeepsOwnerRoleAndMembershipDoesNotChangeOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();

        Project project = projectService.create(ownerId, "Clinica");

        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        AccessibleProject ownerView =
                projectService.listAccessible(ownerId).getFirst();

        assertEquals(
                ProjectAccessRole.OWNER,
                ownerView.accessRole()
        );

        assertTrue(
                projectService
                        .listAccessible(editorId)
                        .stream()
                        .anyMatch(item ->
                                item.project().id().equals(project.id())
                        )
        );

        assertEquals(
                ownerId,
                projectService.get(editorId, project.id()).ownerId()
        );
    }
}
