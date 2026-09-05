package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanNormalizer;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.validation.ProjectDocumentValidator;

final class VisionBenchmarkExecutableGate {

    private final UmlAssistantCommandResolver commandResolver =
            new UmlAssistantCommandResolver(
                    new ProjectCommandExecutor(),
                    new AssistantPlanNormalizer()
            );
    private final ProjectDocumentValidator documentValidator =
            new ProjectDocumentValidator();

    Result evaluate(AssistantSemanticPlan plan, ProjectDocument document) {
        try {
            UmlCommandPayload batch = commandResolver.resolve(plan, document);
            ProjectDocument preview = commandResolver.preview(document, batch);
            documentValidator.validate(preview);
            return new Result(true, "");
        } catch (Exception exception) {
            return new Result(
                    false,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    record Result(boolean accepted, String detail) {
    }
}
