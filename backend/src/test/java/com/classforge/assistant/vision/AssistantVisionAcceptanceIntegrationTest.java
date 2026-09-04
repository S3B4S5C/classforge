package com.classforge.assistant.vision;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu09-acceptance;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "classforge.assistant.vision.provider=llama-cpp"
})
@EnabledIfSystemProperty(named = "assistant.vision.acceptance.enabled", matches = "true")
class AssistantVisionAcceptanceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AssistantImagePlanService imagePlanService;

    @Autowired
    private ProjectCommandExecutor commandExecutor;

    @Test
    void realVisionPlanCanBeAppliedPersistedAndReopenedThroughCanonicalDocumentFlow() throws Exception {
        UUID owner = UUID.randomUUID();
        Project project = projectService.create(owner, "CU09 real image acceptance");

        AssistantImagePlanResponse response = imagePlanService.plan(
                owner,
                project.id(),
                project.revision(),
                fixture("/assistant/vision/class-simple.png")
        );

        assertEquals(AssistantImagePlanDisposition.READY, response.disposition());
        assertNotNull(response.command());
        assertTrue(response.plan().actions().size() >= 1);

        ProjectDocument next = commandExecutor.execute(project.document(), response.command());
        Project saved = projectService.saveDocument(
                owner,
                project.id(),
                project.revision(),
                next
        );

        Project reopened = projectService.get(owner, project.id());
        assertEquals(saved.revision(), reopened.revision());
        assertTrue(
                reopened.document().umlModel().classes().stream()
                        .anyMatch(umlClass -> "Cliente".equals(umlClass.name()))
        );
    }

    private MockMultipartFile fixture(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing acceptance fixture: " + resource);
            }
            return new MockMultipartFile(
                    "image",
                    "class-simple.png",
                    "image/png",
                    input.readAllBytes()
            );
        }
    }
}
