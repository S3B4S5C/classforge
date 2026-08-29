package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantPlanningStage;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.project.domain.document.ProjectDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantNativeToolPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistantNativeToolPlanner.class);

    private final DynamicUmlToolCatalog toolCatalog;
    private final AssistantToolCallingGateway toolCallingGateway;
    private final UmlToolCallResolver toolCallResolver;

    public AssistantNativeToolPlanner(
            DynamicUmlToolCatalog toolCatalog,
            AssistantToolCallingGateway toolCallingGateway,
            UmlToolCallResolver toolCallResolver
    ) {
        this.toolCatalog = toolCatalog;
        this.toolCallingGateway = toolCallingGateway;
        this.toolCallResolver = toolCallResolver;
    }

    public AssistantSemanticPlan plan(
            String userText,
            ProjectDocument document
    ) {
        return planWithDiagnostics(userText, document).plan();
    }

    public AssistantToolPlanningDiagnostics planWithDiagnostics(
            String userText,
            ProjectDocument document
    ) {
        AssistantToolCatalog catalog = toolCatalog.build(userText, document);
        List<AssistantToolInvocation> invocations = toolCallingGateway.call(
                userText,
                document,
                catalog
        );
        AssistantToolResolution resolution;
        try {
            resolution = toolCallResolver.resolve(
                    userText,
                    invocations,
                    catalog,
                    document
            );
        } catch (AssistantPlanningException exception) {
            throw exception.withDiagnostic(
                    AssistantPlanningStage.TOOL_RESOLUTION,
                    null,
                    userText,
                    null
            );
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Assistant native tools: exposed={} calls={} references={}",
                    catalog.definitions().stream().map(def -> def.name().wireName()).toList(),
                    invocations,
                    resolution.references()
            );
        }

        return new AssistantToolPlanningDiagnostics(
                resolution.plan(),
                catalog,
                invocations,
                resolution.references()
        );
    }

    public record AssistantToolPlanningDiagnostics(
            AssistantSemanticPlan plan,
            AssistantToolCatalog catalog,
            List<AssistantToolInvocation> invocations,
            List<AssistantResolvedReference> references
    ) {
    }
}
