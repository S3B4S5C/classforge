package com.classforge.assistant;

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
}