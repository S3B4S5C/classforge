package com.classforge.assistant;

import com.classforge.assistant.vision.VisionModelGatewayException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssistantPlanningDiagnosticsTests {

    @Test
    void errorBodyIncludesStageTranscriptAndAttemptedPlan() {
        AssistantSemanticPlan attempted =
                new AssistantSemanticPlan(
                        "Relacion",
                        List.of()
                );

        AssistantPlanningException exception =
                new AssistantPlanningException(
                        "Plan rechazado"
                ).withDiagnostic(
                        AssistantPlanningStage.GROUNDING,
                        "VOICE",
                        "Conecta Animal con Veterinario",
                        attempted
                );

        AssistantController controller =
                new AssistantController(
                        null,
                        null,
                        null,
                        null,
                        null
                );

        Map<String, Object> body =
                controller.handle(
                        exception
                );

        assertEquals(
                "GROUNDING",
                body.get(
                        "stage"
                )
        );

        assertEquals(
                "VOICE",
                body.get(
                        "source"
                )
        );

        assertEquals(
                "Conecta Animal con Veterinario",
                body.get(
                        "transcript"
                )
        );

        assertSame(
                attempted,
                body.get(
                        "attemptedPlan"
                )
        );

        assertFalse(
                body.containsKey(
                        "prompt"
                )
        );
    }

    @Test
    void imageDiagnosticPreservesVisionReason() {
        assertVisionReason(VisionModelGatewayException.Reason.OUTPUT_CONTRACT);
        assertVisionReason(VisionModelGatewayException.Reason.TRANSPORT);
    }

    private void assertVisionReason(VisionModelGatewayException.Reason reason) {
        AssistantPlanningException exception = new VisionModelGatewayException(reason, "hybrid rejected")
                .withDiagnostic(AssistantPlanningStage.VISION, "IMAGE", "imagen: board.png", null);
        Map<String, Object> body = new AssistantController(null, null, null, null, null).handle(exception);

        assertEquals("ASSISTANT_PLANNING_ERROR", body.get("error"));
        assertEquals("VISION", body.get("stage"));
        assertEquals("IMAGE", body.get("source"));
        assertEquals(reason.name(), body.get("visionReason"));
    }
}
