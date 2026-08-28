package com.classforge.assistant;

import com.classforge.project.application.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class AssistantVoiceService {

    private final ProjectService projectService;
    private final SpeechToTextGateway speechToTextGateway;
    private final AssistantPlanService planService;

    public AssistantVoiceService(
            ProjectService projectService,
            SpeechToTextGateway speechToTextGateway,
            AssistantPlanService planService
    ) {
        this.projectService =
                projectService;

        this.speechToTextGateway =
                speechToTextGateway;

        this.planService =
                planService;
    }

    public AssistantPlanResponse plan(
            UUID ownerId,
            UUID projectId,
            MultipartFile audio
    ) {
        projectService.get(
                ownerId,
                projectId
        );

        if (
                audio == null
                        || audio.isEmpty()
        ) {
            throw new AssistantPlanningException(
                    "No se recibio audio para transcribir."
            );
        }

        if (
                audio.getSize()
                        > WhisperCppSpeechToTextGateway.MAX_AUDIO_BYTES
        ) {
            throw new AssistantPlanningException(
                    "El audio supera el limite de 4 MB."
            );
        }

        String transcript;

        try {
            transcript =
                    speechToTextGateway.transcribe(
                            audio.getBytes(),
                            audio.getOriginalFilename()
                    );
        } catch (
                AssistantPlanningException exception
        ) {
            throw exception.withDiagnostic(
                    AssistantPlanningStage.STT,
                    "VOICE",
                    null,
                    null
            );
        } catch (
                IOException exception
        ) {
            throw new AssistantPlanningException(
                    "No pudimos leer el audio recibido.",
                    exception
            ).withDiagnostic(
                    AssistantPlanningStage.STT,
                    "VOICE",
                    null,
                    null
            );
        }

        return planService.planVoice(
                ownerId,
                projectId,
                transcript
        );
    }
}