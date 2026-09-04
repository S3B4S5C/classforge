package com.classforge.assistant;

import com.classforge.project.application.ProjectService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantRuntimeHealthServiceTests {

    @Test
    void textVoiceAndImageHaveIndependentReadinessRules() {
        ProjectService projectService = mock(ProjectService.class);
        AssistantRuntimeProbe probe = mock(AssistantRuntimeProbe.class);
        AssistantVisionRuntimeProbe visionProbe = mock(AssistantVisionRuntimeProbe.class);

        when(probe.probe("llama.cpp", "http://127.0.0.1:8092"))
                .thenReturn(ready("llama.cpp"));
        when(probe.probe("whisper.cpp", "http://127.0.0.1:8093"))
                .thenReturn(down("whisper.cpp"));
        when(visionProbe.probe("http://127.0.0.1:8094", "vision-model"))
                .thenReturn(ready("vision"));

        AssistantRuntimeHealthService service = new AssistantRuntimeHealthService(
                projectService,
                probe,
                visionProbe,
                "http://127.0.0.1:8092",
                "http://127.0.0.1:8093",
                "http://127.0.0.1:8094",
                "vision-model"
        );

        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        AssistantRuntimeHealthResponse health = service.health(ownerId, projectId);

        assertTrue(health.readyForText());
        assertFalse(health.readyForVoice());
        assertTrue(health.readyForImage());
        verify(projectService).get(ownerId, projectId);
    }

    @Test
    void imageIsDownWithoutAValidVisionRuntimeButTextAndVoiceStayReady() {
        ProjectService projectService = mock(ProjectService.class);
        AssistantRuntimeProbe probe = mock(AssistantRuntimeProbe.class);
        AssistantVisionRuntimeProbe visionProbe = mock(AssistantVisionRuntimeProbe.class);

        when(probe.probe("llama.cpp", "llama")).thenReturn(ready("llama.cpp"));
        when(probe.probe("whisper.cpp", "whisper")).thenReturn(ready("whisper.cpp"));
        when(visionProbe.probe("vision", "vision-model")).thenReturn(down("vision"));

        AssistantRuntimeHealthResponse health = new AssistantRuntimeHealthService(
                projectService,
                probe,
                visionProbe,
                "llama",
                "whisper",
                "vision",
                "vision-model"
        ).health(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(health.readyForText());
        assertTrue(health.readyForVoice());
        assertFalse(health.readyForImage());
    }

    private AssistantRuntimeStatus ready(String name) {
        return new AssistantRuntimeStatus(name, true, "READY", 1, "Listo");
    }

    private AssistantRuntimeStatus down(String name) {
        return new AssistantRuntimeStatus(name, false, "UNREACHABLE", 1, "No responde");
    }
}
