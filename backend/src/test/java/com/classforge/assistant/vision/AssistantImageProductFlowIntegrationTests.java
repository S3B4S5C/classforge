package com.classforge.assistant.vision;

import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.collaboration.application.ProjectCollaborationService;
import com.classforge.collaboration.application.ProjectOperationRejectedException;
import com.classforge.collaboration.protocol.ProjectOperationApplied;
import com.classforge.collaboration.protocol.ProjectOperationRequest;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu09-image-product-flow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AssistantImageProductFlowIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectCollaborationService collaborationService;

    @Autowired
    private UserRepository userRepository;

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
    void imagePlanBatchPreviewApplyPersistsAndReopensExactlyOnce() throws Exception {
        UUID owner = createOwner();
        Project project = projectService.create(owner, "CU09 image product flow");
        long initialRevision = project.revision();
        ProjectDocument initialDocument = project.document();

        assertEquals(0L, initialRevision);

        AssistantImagePlanResponse response = service(proposal()).plan(
                owner, project.id(), initialRevision, image()
        );

        assertEquals("IMAGE", response.source());
        assertEquals(AssistantImagePlanDisposition.READY, response.disposition());
        assertEquals(initialRevision, response.baseRevision());
        assertNotNull(response.command());
        assertEquals(UmlCommandType.BATCH, response.command().type());
        assertTrue(response.command().safeCommands().size() > 1);
        assertFalse(response.plan().actions().isEmpty());

        Project beforeApply = projectService.get(owner, project.id());
        assertEquals(initialRevision, beforeApply.revision());
        assertEquals(initialDocument, beforeApply.document());
        assertTrue(beforeApply.document().umlModel().classes().isEmpty());
        assertTrue(beforeApply.document().umlModel().relationships().isEmpty());

        ProjectDocument expectedPreview = commandResolver.preview(initialDocument, response.command());
        documentValidator.validate(expectedPreview);

        ProjectOperationRequest operation = operation(project.id(), response.baseRevision(), response.command());
        ProjectOperationApplied applied = collaborationService.apply(owner, project.id(), operation);

        assertEquals(project.id(), applied.projectId());
        assertEquals(operation.operationId(), applied.operationId());
        assertEquals(operation.clientId(), applied.clientId());
        assertEquals(response.command(), applied.command());
        assertEquals(initialRevision + 1, applied.revision());

        Project reopened = projectService.get(owner, project.id());
        assertEquals(initialRevision + 1, reopened.revision());
        assertEquals(expectedPreview, reopened.document());

        assertEquals(2, reopened.document().umlModel().classes().size());
        UmlClass cliente = umlClass(reopened, "Cliente");
        UmlClass factura = umlClass(reopened, "Factura");
        assertTrue(cliente.attributes().stream().anyMatch(attribute ->
                "email".equals(attribute.name()) && attribute.dataType() == UmlDataType.STRING
        ));
        assertTrue(factura.attributes().stream().anyMatch(attribute ->
                "total".equals(attribute.name()) && attribute.dataType() == UmlDataType.DECIMAL
        ));

        assertEquals(1, reopened.document().umlModel().relationships().size());
        UmlRelationship relationship = reopened.document().umlModel().relationships().getFirst();
        assertEquals(UmlRelationshipType.ASSOCIATION, relationship.type());
        assertEquals(cliente.id(), relationship.sourceClassId());
        assertEquals(factura.id(), relationship.targetClassId());
        assertEquals(new Multiplicity(1, 1), relationship.sourceMultiplicity());
        assertEquals(new Multiplicity(0, null), relationship.targetMultiplicity());
    }

    @Test
    void staleAssistantImagePlanIsRejectedByCanonicalApplyWithoutAssistantMutation() throws Exception {
        UUID owner = createOwner();
        Project project = projectService.create(owner, "CU09 stale image product flow");

        AssistantImagePlanResponse response = service(proposal()).plan(owner, project.id(), 0L, image());
        assertEquals(AssistantImagePlanDisposition.READY, response.disposition());

        collaborationService.apply(
                owner,
                project.id(),
                operation(project.id(), 0L, externalCreateClass("Intervenida"))
        );

        ProjectOperationRejectedException exception = assertThrows(
                ProjectOperationRejectedException.class,
                () -> collaborationService.apply(
                        owner,
                        project.id(),
                        operation(project.id(), response.baseRevision(), response.command())
                )
        );

        assertEquals("REVISION_CONFLICT", exception.getCode());
        assertEquals(1L, exception.getCurrentRevision());

        Project reopened = projectService.get(owner, project.id());
        assertEquals(1L, reopened.revision());
        assertNotNull(umlClass(reopened, "Intervenida"));
        assertFalse(reopened.document().umlModel().classes().stream()
                .anyMatch(umlClass -> "Cliente".equals(umlClass.name())));
        assertFalse(reopened.document().umlModel().classes().stream()
                .anyMatch(umlClass -> "Factura".equals(umlClass.name())));
        assertTrue(reopened.document().umlModel().relationships().isEmpty());
    }

    private UUID createOwner() {
        UUID ownerId = UUID.randomUUID();
        userRepository.save(new UserEntity(
                ownerId,
                "CU09 Owner",
                "cu09-owner-" + ownerId + "@classforge.test",
                "unused",
                Instant.now()
        ));
        return ownerId;
    }

    private AssistantImagePlanService service(VisionUmlProposal proposal) {
        return new AssistantImagePlanService(
                projectService,
                imageInputValidator,
                imageNormalizer,
                (image, context) -> proposal,
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
                        new VisionClassProposal("cliente", "Cliente", List.of(
                                new VisionAttributeProposal("email", "STRING", null, "PRIVATE", true, false, evidence("email"))
                        ), evidence("Cliente")),
                        new VisionClassProposal("factura", "Factura", List.of(
                                new VisionAttributeProposal("total", "DECIMAL", null, "PRIVATE", true, false, evidence("total"))
                        ), evidence("Factura"))
                ),
                List.of(new VisionRelationshipProposal(
                        "cliente", "factura", "ASSOCIATION",
                        new VisionMultiplicityProposal(1, 1, false),
                        new VisionMultiplicityProposal(0, null, true),
                        evidence("Cliente Factura")
                )),
                List.of(),
                0.95
        );
    }

    private VisionEvidence evidence(String label) {
        return new VisionEvidence(label, 0.95, 10, 10, 120, 50);
    }

    private ProjectOperationRequest operation(UUID projectId, long baseRevision, UmlCommandPayload command) {
        return new ProjectOperationRequest(UUID.randomUUID(), projectId, UUID.randomUUID(), baseRevision, command);
    }

    private UmlCommandPayload externalCreateClass(String name) {
        return new UmlCommandPayload(
                UUID.randomUUID(), Instant.now(), UmlCommandType.CREATE_CLASS, null, null,
                new UmlClass(UUID.randomUUID(), name, List.of()),
                new DiagramNodeLayout(80, 80, 260, 160), null, null, null, null
        );
    }

    private UmlClass umlClass(Project project, String name) {
        return project.document().umlModel().classes().stream()
                .filter(umlClass -> name.equals(umlClass.name()))
                .findFirst()
                .orElseThrow();
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
        return new MockMultipartFile("image", "diagram.png", "image/png", output.toByteArray());
    }
}
