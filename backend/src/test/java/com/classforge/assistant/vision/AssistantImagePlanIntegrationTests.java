package com.classforge.assistant.vision;

import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu09-image-contract;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AssistantImagePlanIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private AssistantImageInputValidator imageInputValidator;

    @Autowired
    private VisionImageNormalizer imageNormalizer;

    @Autowired
    private VisionProposalGroundingValidator groundingValidator;

    @Autowired
    private VisionEvidenceBoundsValidator evidenceBoundsValidator;

    @Autowired
    private VisionEvidencePresenter evidencePresenter;

    @Autowired
    private VisionProposalCompiler proposalCompiler;

    @Autowired
    private UmlAssistantCommandResolver commandResolver;

    @Autowired
    private ProjectDocumentValidator documentValidator;

    @Test
    void ownerAndEditorCanProduceCanonicalBatchButNoneNeverInvokesVision() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        Project project = projectService.create(owner, "CU09 Vision Contract");
        projectMembershipRepository.save(ProjectMembershipEntity.editor(project.id(), editor));

        AtomicInteger calls = new AtomicInteger();
        VisionModelGateway gateway = (image, context) -> {
            calls.incrementAndGet();
            return proposal();
        };
        AssistantImagePlanService service = service(gateway);
        MockMultipartFile image = image();

        AssistantImagePlanResponse ownerPlan = service.plan(owner, project.id(), 0L, image);
        AssistantImagePlanResponse editorPlan = service.plan(editor, project.id(), 0L, image);

        assertEquals("IMAGE", ownerPlan.source());
        assertEquals(AssistantImagePlanDisposition.READY, ownerPlan.disposition());
        assertEquals(UmlCommandType.BATCH, ownerPlan.command().type());
        assertEquals(4, ownerPlan.evidence().size());
        assertEquals(3, ownerPlan.plan().actions().size());
        assertEquals(UmlCommandType.BATCH, editorPlan.command().type());
        assertEquals(2, calls.get());

        assertThrows(
                ProjectNotFoundException.class,
                () -> service.plan(none, project.id(), 0L, image)
        );
        assertEquals(2, calls.get());
    }

    @Test
    void staleRevisionIsRejectedBeforeVision() throws Exception {
        UUID owner = UUID.randomUUID();
        Project project = projectService.create(owner, "CU09 stale");
        AtomicInteger calls = new AtomicInteger();
        AssistantImagePlanService service = service((image, context) -> {
            calls.incrementAndGet();
            return proposal();
        });

        assertThrows(
                ProjectRevisionConflictException.class,
                () -> service.plan(owner, project.id(), 99L, image())
        );
        assertEquals(0, calls.get());
    }

    @Test
    void emptyVisualProposalReturnsNoActionableWithoutCommand() throws Exception {
        UUID owner = UUID.randomUUID();
        Project project = projectService.create(owner, "CU09 empty visual");
        AssistantImagePlanService service = service((image, context) -> new VisionUmlProposal(
                "No UML",
                List.of(),
                List.of(),
                List.of("No se detectaron simbolos UML legibles."),
                0.2
        ));

        AssistantImagePlanResponse response = service.plan(owner, project.id(), 0L, image());

        assertEquals(AssistantImagePlanDisposition.NO_ACTIONABLE_UML, response.disposition());
        assertEquals(null, response.command());
        assertEquals(0, response.plan().actions().size());
    }

    @Test
    void hybridGatewayFailureProducesVisionDiagnosticAndNoPlan() throws Exception {
        UUID owner = UUID.randomUUID();
        Project project = projectService.create(owner, "CU09 hybrid failure");
        AssistantImagePlanService service = service((image, context) -> {
            throw new VisionModelGatewayException(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, "hybrid rejected");
        });

        AssistantPlanningException exception = assertThrows(
                AssistantPlanningException.class,
                () -> service.plan(owner, project.id(), 0L, image())
        );

        assertEquals(com.classforge.assistant.AssistantPlanningStage.VISION, exception.stage());
        assertEquals("IMAGE", exception.source());
        VisionModelGatewayException cause = (VisionModelGatewayException) exception.getCause();
        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, cause.reason());
    }

    private AssistantImagePlanService service(VisionModelGateway gateway) {
        return new AssistantImagePlanService(
                projectService,
                imageInputValidator,
                imageNormalizer,
                gateway,
                groundingValidator,
                evidenceBoundsValidator,
                evidencePresenter,
                proposalCompiler,
                commandResolver,
                documentValidator
        );
    }

    private VisionUmlProposal proposal() {
        return new VisionUmlProposal(
                "Crear Cliente y Factura desde imagen",
                List.of(
                        new VisionClassProposal(
                                "cliente", "Cliente",
                                List.of(
                                        new VisionAttributeProposal(
                                                "email", "STRING", null, "PRIVATE", true, false,
                                                evidence("email")
                                        )
                                ),
                                evidence("Cliente")
                        ),
                        new VisionClassProposal("factura", "Factura", List.of(), evidence("Factura"))
                ),
                List.of(
                        new VisionRelationshipProposal(
                                "cliente", "factura", "ASSOCIATION", null, null,
                                evidence("Cliente Factura")
                        )
                ),
                List.of(),
                0.95
        );
    }

    private VisionEvidence evidence(String label) {
        return new VisionEvidence(label, 0.95, 10, 10, 120, 50);
    }

    private MockMultipartFile image() throws Exception {
        BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                image.setRGB(x, y, 0x00ffffff);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "image", "diagram.png", "image/png", output.toByteArray()
        );
    }
}
