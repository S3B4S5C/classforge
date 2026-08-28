package com.classforge.assistant;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AssistantPlanService {

    private final ProjectService projectService;
    private final LanguageModelGateway languageModelGateway;
    private final AssistantPlanGroundingFilter groundingFilter;
    private final AssistantPlanNormalizer normalizer;
    private final UmlAssistantCommandResolver resolver;
    private final ProjectDocumentValidator validator;

    public AssistantPlanService(
            ProjectService projectService,
            LanguageModelGateway languageModelGateway,
            AssistantPlanGroundingFilter groundingFilter,
            AssistantPlanNormalizer normalizer,
            UmlAssistantCommandResolver resolver,
            ProjectDocumentValidator validator
    ) {
        this.projectService = projectService;
        this.languageModelGateway = languageModelGateway;
        this.groundingFilter = groundingFilter;
        this.normalizer = normalizer;
        this.resolver = resolver;
        this.validator = validator;
    }

    public AssistantPlanResponse plan(
            UUID ownerId,
            UUID projectId,
            String text
    ) {
        return plan(
                ownerId,
                projectId,
                text,
                "TEXT"
        );
    }

    public AssistantPlanResponse planVoice(
            UUID ownerId,
            UUID projectId,
            String transcript
    ) {
        return plan(
                ownerId,
                projectId,
                transcript,
                "VOICE"
        );
    }

    private AssistantPlanResponse plan(
            UUID ownerId,
            UUID projectId,
            String text,
            String source
    ) {
        if (
                text == null
                        || text.isBlank()
        ) {
            throw new AssistantPlanningException(
                    "La instruccion no puede estar vacia"
            );
        }

        String userText =
                text.trim();

        Project project =
                projectService.get(
                        ownerId,
                        projectId
                );

        AssistantSemanticPlan rawPlan;

        try {
            rawPlan =
                    languageModelGateway.plan(
                            userText,
                            project.document()
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.LLM,
                    source,
                    userText,
                    null
            );
        }

        AssistantSemanticPlan groundedPlan;

        try {
            groundedPlan =
                    groundingFilter.sanitize(
                            userText,
                            rawPlan,
                            project.document()
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.GROUNDING,
                    source,
                    userText,
                    rawPlan
            );
        }

        AssistantSemanticPlan normalizedPlan;

        try {
            normalizedPlan =
                    normalizer.normalize(
                            groundedPlan
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.NORMALIZATION,
                    source,
                    userText,
                    rawPlan
            );
        }

        UmlCommandPayload batch;

        try {
            batch =
                    resolver.resolve(
                            normalizedPlan,
                            project.document()
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.RESOLUTION,
                    source,
                    userText,
                    rawPlan
            );
        }

        ProjectDocument preview;

        try {
            preview =
                    resolver.preview(
                            project.document(),
                            batch
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw diagnostic(
                    exception,
                    AssistantPlanningStage.PREVIEW,
                    source,
                    userText,
                    rawPlan
            );
        }

        validator.validate(
                preview
        );

        return new AssistantPlanResponse(
                source,
                userText,
                project.revision(),
                normalizedPlan.summary(),
                normalizedPlan,
                batch
        );
    }

    private AssistantPlanningException diagnostic(
            AssistantPlanningException exception,
            AssistantPlanningStage stage,
            String source,
            String transcript,
            AssistantSemanticPlan attemptedPlan
    ) {
        return exception.withDiagnostic(
                stage,
                source,
                transcript,
                attemptedPlan
        );
    }
}