package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "classforge.assistant.vision.provider",
        havingValue = "unconfigured",
        matchIfMissing = true
)
public class UnconfiguredVisionModelGateway implements VisionModelGateway {

    @Override
    public VisionUmlProposal analyze(
            VisionNormalizedImage image,
            VisionProjectContext context
    ) {
        throw new AssistantPlanningException(
                "CU-09 tiene preparado el contrato de vision, pero todavia no hay un VLM local configurado. "
                        + "Selecciona y conecta el modelo en C2-cu09-002."
        );
    }
}
