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
    void textNeedsLlamaAndVoiceNeedsBothRuntimes() {
        ProjectService projectService =
                mock(
                        ProjectService.class
                );

        AssistantRuntimeProbe probe =
                mock(
                        AssistantRuntimeProbe.class
                );

        when(
                probe.probe(
                        "llama.cpp",
                        "http://127.0.0.1:8092"
                )
        ).thenReturn(
                new AssistantRuntimeStatus(
                        "llama.cpp",
                        true,
                        "READY",
                        4,
                        "Listo"
                )
        );

        when(
                probe.probe(
                        "whisper.cpp",
                        "http://127.0.0.1:8093"
                )
        ).thenReturn(
                new AssistantRuntimeStatus(
                        "whisper.cpp",
                        false,
                        "UNREACHABLE",
                        12,
                        "No responde"
                )
        );

        AssistantRuntimeHealthService service =
                new AssistantRuntimeHealthService(
                        projectService,
                        probe,
                        "http://127.0.0.1:8092",
                        "http://127.0.0.1:8093"
                );

        UUID ownerId =
                UUID.randomUUID();

        UUID projectId =
                UUID.randomUUID();

        AssistantRuntimeHealthResponse health =
                service.health(
                        ownerId,
                        projectId
                );

        assertTrue(
                health.readyForText()
        );

        assertFalse(
                health.readyForVoice()
        );

        verify(
                projectService
        ).get(
                ownerId,
                projectId
        );
    }

    @Test
    void voiceIsReadyWhenBothRuntimesAreReady() {
        ProjectService projectService =
                mock(
                        ProjectService.class
                );

        AssistantRuntimeProbe probe =
                mock(
                        AssistantRuntimeProbe.class
                );

        when(
                probe.probe(
                        "llama.cpp",
                        "llama"
                )
        ).thenReturn(
                ready(
                        "llama.cpp"
                )
        );

        when(
                probe.probe(
                        "whisper.cpp",
                        "whisper"
                )
        ).thenReturn(
                ready(
                        "whisper.cpp"
                )
        );

        AssistantRuntimeHealthResponse health =
                new AssistantRuntimeHealthService(
                        projectService,
                        probe,
                        "llama",
                        "whisper"
                ).health(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );

        assertTrue(
                health.readyForText()
        );

        assertTrue(
                health.readyForVoice()
        );
    }

    private AssistantRuntimeStatus ready(
            String name
    ) {
        return new AssistantRuntimeStatus(
                name,
                true,
                "READY",
                1,
                "Listo"
        );
    }
}