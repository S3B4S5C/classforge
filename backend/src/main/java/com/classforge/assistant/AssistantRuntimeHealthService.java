package com.classforge.assistant;

import com.classforge.project.application.ProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssistantRuntimeHealthService {

    private final ProjectService projectService;
    private final AssistantRuntimeProbe runtimeProbe;
    private final String llamaUrl;
    private final String whisperUrl;

    public AssistantRuntimeHealthService(
            ProjectService projectService,
            AssistantRuntimeProbe runtimeProbe,
            @Value("${classforge.assistant.llama-url:http://127.0.0.1:8092}")
            String llamaUrl,
            @Value("${classforge.assistant.whisper-url:http://127.0.0.1:8093}")
            String whisperUrl
    ) {
        this.projectService =
                projectService;

        this.runtimeProbe =
                runtimeProbe;

        this.llamaUrl =
                llamaUrl;

        this.whisperUrl =
                whisperUrl;
    }

    public AssistantRuntimeHealthResponse health(
            UUID userId,
            UUID projectId
    ) {
        projectService.get(
                userId,
                projectId
        );

        AssistantRuntimeStatus llama =
                runtimeProbe.probe(
                        "llama.cpp",
                        llamaUrl
                );

        AssistantRuntimeStatus whisper =
                runtimeProbe.probe(
                        "whisper.cpp",
                        whisperUrl
                );

        return new AssistantRuntimeHealthResponse(
                llama.available(),
                llama.available()
                        && whisper.available(),
                llama,
                whisper,
                Instant.now()
        );
    }
}