package com.classforge.assistant;

import com.classforge.assistant.tools.AssistantNativeToolPlanner;
import com.classforge.project.domain.document.ProjectDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AssistantPlanningRouter implements LanguageModelGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistantPlanningRouter.class);

    private final LlamaLanguageModelGateway legacyGateway;
    private final AssistantNativeToolPlanner nativeToolPlanner;
    private final AssistantPlannerMode mode;

    public AssistantPlanningRouter(
            LlamaLanguageModelGateway legacyGateway,
            AssistantNativeToolPlanner nativeToolPlanner,
            @Value("${classforge.assistant.planner-mode:legacy}") String mode
    ) {
        this.legacyGateway = legacyGateway;
        this.nativeToolPlanner = nativeToolPlanner;
        this.mode = AssistantPlannerMode.parse(mode);
    }

    @Override
    public AssistantSemanticPlan plan(
            String userText,
            ProjectDocument document
    ) {
        return switch (mode) {
            case LEGACY -> legacyGateway.plan(userText, document);
            case TOOLS -> nativeToolPlanner.plan(userText, document);
            case COMPARE -> compare(userText, document);
        };
    }

    AssistantPlannerMode mode() {
        return mode;
    }

    private AssistantSemanticPlan compare(
            String userText,
            ProjectDocument document
    ) {
        AssistantSemanticPlan legacy = null;
        RuntimeException legacyFailure = null;

        try {
            legacy = legacyGateway.plan(userText, document);
        } catch (RuntimeException exception) {
            legacyFailure = exception;
        }

        AssistantSemanticPlan tools = nativeToolPlanner.plan(userText, document);

        if (legacyFailure != null) {
            LOGGER.info(
                    "Assistant compare: legacy fallo y tools produjo plan. legacyError={}",
                    legacyFailure.getMessage()
            );
        } else if (!tools.equals(legacy)) {
            LOGGER.info(
                    "Assistant compare: legacy y tools difieren. legacy={} tools={}",
                    legacy,
                    tools
            );
        }

        return tools;
    }
}
