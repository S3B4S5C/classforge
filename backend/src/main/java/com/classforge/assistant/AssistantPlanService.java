package com.classforge.assistant;

import com.classforge.assistant.tools.AssistantNativeToolPlanner;
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
    private final AssistantNativeToolPlanner nativeToolPlanner;
    private final UmlAssistantCommandResolver resolver;
    private final ProjectDocumentValidator validator;

    public AssistantPlanService(
            ProjectService projectService,
            AssistantNativeToolPlanner nativeToolPlanner,
            UmlAssistantCommandResolver resolver,
            ProjectDocumentValidator validator
    ) {
        this.projectService = projectService;
        this.nativeToolPlanner = nativeToolPlanner;
        this.resolver = resolver;
        this.validator = validator;
    }

    public AssistantPlanResponse plan(
            UUID userId,
            UUID projectId,
            String text
    ) {
        return plan(
                userId,
                projectId,
                text,
                "TEXT"
        );
    }

    public AssistantPlanResponse planVoice(
            UUID userId,
            UUID projectId,
            String transcript
    ) {
        return plan(
                userId,
                projectId,
                transcript,
                "VOICE"
        );
    }

    private AssistantPlanResponse plan(
            UUID userId,
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
                        userId,
                        projectId
                );

        AssistantSemanticPlan rawPlan;

        try {
            rawPlan =
                    nativeToolPlanner.plan(
                            userText,
                            project.document()
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            AssistantPlanningStage stage = exception.stage() == null
                    ? AssistantPlanningStage.LLM
                    : exception.stage();
            throw diagnostic(
                    exception,
                    stage,
                    source,
                    userText,
                    exception.attemptedPlan()
            );
        }

        // Native tools already return a semantically compiled, grounded and normalized plan.
        AssistantSemanticPlan normalizedPlan = rawPlan;

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