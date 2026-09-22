package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantPlanningStage;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AssistantImagePlanService {

    private final ProjectService projectService;
    private final AssistantImageInputValidator imageInputValidator;
    private final VisionImageNormalizer imageNormalizer;
    private final VisionModelGateway visionModelGateway;
    private final VisionProposalGroundingValidator groundingValidator;
    private final VisionEvidenceBoundsValidator evidenceBoundsValidator;
    private final VisionEvidencePresenter evidencePresenter;
    private final VisionProposalCompiler proposalCompiler;
    private final UmlAssistantCommandResolver commandResolver;
    private final ProjectDocumentValidator documentValidator;

    public AssistantImagePlanService(
            ProjectService projectService,
            AssistantImageInputValidator imageInputValidator,
            VisionImageNormalizer imageNormalizer,
            VisionModelGateway visionModelGateway,
            VisionProposalGroundingValidator groundingValidator,
            VisionEvidenceBoundsValidator evidenceBoundsValidator,
            VisionEvidencePresenter evidencePresenter,
            VisionProposalCompiler proposalCompiler,
            UmlAssistantCommandResolver commandResolver,
            ProjectDocumentValidator documentValidator
    ) {
        this.projectService = projectService;
        this.imageInputValidator = imageInputValidator;
        this.imageNormalizer = imageNormalizer;
        this.visionModelGateway = visionModelGateway;
        this.groundingValidator = groundingValidator;
        this.evidenceBoundsValidator = evidenceBoundsValidator;
        this.evidencePresenter = evidencePresenter;
        this.proposalCompiler = proposalCompiler;
        this.commandResolver = commandResolver;
        this.documentValidator = documentValidator;
    }

    public AssistantImagePlanResponse plan(
            UUID userId,
            UUID projectId,
            long baseRevision,
            MultipartFile image
    ) {
        Project project = projectService.get(userId, projectId);
        requireRevision(project, baseRevision);

        VisionImageInput validated = imageInputValidator.validate(image);
        VisionNormalizedImage normalized = imageNormalizer.normalize(validated);
        String sourceDescription = "imagen: " + normalized.originalFilename();

        VisionUmlProposal proposal;
        try {
            proposal = visionModelGateway.analyze(
                    normalized,
                    context(project)
            );
        } catch (AssistantPlanningException exception) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.VISION,
                    sourceDescription,
                    null
            );
        } catch (Exception exception) {
            throw diagnostic(
                    new AssistantPlanningException(
                            "No pudimos ejecutar el gateway visual local.",
                            exception
                    ),
                    AssistantPlanningStage.VISION,
                    sourceDescription,
                    null
            );
        }

        // A potentially slow VLM must never return a preview over a stale revision.
        Project freshProject = projectService.get(userId, projectId);
        requireRevision(freshProject, baseRevision);
        ProjectDocument document = freshProject.document();

        try {
            groundingValidator.validate(proposal);
            evidenceBoundsValidator.validate(
                    proposal,
                    normalized.width(),
                    normalized.height()
            );
        } catch (AssistantPlanningException exception) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.VISION_GROUNDING,
                    sourceDescription,
                    null
            );
        }

        AssistantImageMetadata metadata = metadata(normalized, validated);
        List<AssistantImageEvidenceItem> evidence = evidencePresenter.items(proposal);

        if (proposal.safeClasses().isEmpty()
                && proposal.safeRelationships().isEmpty()
                && proposal.safeAssociationClasses().isEmpty()) {
            List<String> warnings = new ArrayList<>(proposal.safeWarnings());
            if (warnings.isEmpty()) {
                warnings.add("La imagen no contiene UML accionable con suficiente evidencia.");
            }
            return nonActionableResponse(
                    sourceDescription,
                    baseRevision,
                    "No se detecto UML accionable en la imagen.",
                    warnings,
                    proposal.confidence() == null ? 0.0 : proposal.confidence(),
                    metadata,
                    AssistantImagePlanDisposition.NO_ACTIONABLE_UML,
                    evidence
            );
        }

        VisionCompilationResult compilation;
        try {
            compilation = proposalCompiler.compile(proposal, document);
        } catch (AssistantPlanningException exception) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.VISION_GROUNDING,
                    sourceDescription,
                    null
            );
        }

        if (compilation.plan().actions().isEmpty()) {
            List<String> warnings = new ArrayList<>(compilation.warnings());
            if (warnings.isEmpty()) {
                warnings.add("Los elementos detectados ya existen en el proyecto.");
            }
            return nonActionableResponse(
                    sourceDescription,
                    baseRevision,
                    "No se detectaron cambios UML nuevos.",
                    warnings,
                    compilation.confidence(),
                    metadata,
                    AssistantImagePlanDisposition.NO_CHANGES,
                    evidence
            );
        }

        UmlCommandPayload batch;
        try {
            batch = commandResolver.resolve(compilation.plan(), document);
        } catch (AssistantPlanningException exception) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.RESOLUTION,
                    sourceDescription,
                    compilation.plan()
            );
        }

        ProjectDocument preview;
        try {
            preview = commandResolver.preview(document, batch);
        } catch (AssistantPlanningException exception) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.PREVIEW,
                    sourceDescription,
                    compilation.plan()
            );
        }

        documentValidator.validate(preview);

        return new AssistantImagePlanResponse(
                "IMAGE",
                sourceDescription,
                baseRevision,
                compilation.plan().summary(),
                compilation.plan(),
                batch,
                compilation.warnings(),
                compilation.confidence(),
                metadata,
                AssistantImagePlanDisposition.READY,
                evidence
        );
    }

    private AssistantImagePlanResponse nonActionableResponse(
            String sourceDescription,
            long baseRevision,
            String summary,
            List<String> warnings,
            double confidence,
            AssistantImageMetadata metadata,
            AssistantImagePlanDisposition disposition,
            List<AssistantImageEvidenceItem> evidence
    ) {
        return new AssistantImagePlanResponse(
                "IMAGE",
                sourceDescription,
                baseRevision,
                summary,
                new AssistantSemanticPlan(summary, List.of()),
                null,
                List.copyOf(warnings),
                confidence,
                metadata,
                disposition,
                evidence
        );
    }

    private AssistantImageMetadata metadata(
            VisionNormalizedImage normalized,
            VisionImageInput validated
    ) {
        return new AssistantImageMetadata(
                normalized.originalFilename(),
                normalized.originalMediaType(),
                normalized.mediaType(),
                normalized.width(),
                normalized.height(),
                validated.bytes().length,
                normalized.sha256(),
                normalized.reencoded()
        );
    }

    private VisionProjectContext context(Project project) {
        List<VisionExistingClassContext> classes = project.document()
                .umlModel()
                .classes()
                .stream()
                .map(umlClass -> new VisionExistingClassContext(
                        umlClass.id(),
                        umlClass.name(),
                        umlClass.attributes().stream().map(attribute -> attribute.name()).toList()
                ))
                .toList();

        return new VisionProjectContext(
                project.id(),
                project.revision(),
                classes
        );
    }

    private void requireRevision(Project project, long requestedRevision) {
        if (requestedRevision < 0) {
            throw new AssistantImageValidationException("baseRevision no puede ser negativa.");
        }
        if (project.revision() != requestedRevision) {
            throw new ProjectRevisionConflictException(
                    requestedRevision,
                    project.revision()
            );
        }
    }

    private AssistantPlanningException diagnostic(
            AssistantPlanningException exception,
            AssistantPlanningStage stage,
            String transcript,
            com.classforge.assistant.AssistantSemanticPlan attemptedPlan
    ) {
        return exception.withDiagnostic(
                stage,
                "IMAGE",
                transcript,
                attemptedPlan
        );
    }
}
