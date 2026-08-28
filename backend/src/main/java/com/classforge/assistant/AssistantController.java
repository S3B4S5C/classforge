package com.classforge.assistant;

import com.classforge.auth.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(
        "/api/projects/{projectId}/assistant"
)
public class AssistantController {

    private final AssistantPlanService service;
    private final AssistantVoiceService voiceService;
    private final AssistantRuntimeHealthService runtimeHealthService;
    private final CurrentUser currentUser;

    public AssistantController(
            AssistantPlanService service,
            AssistantVoiceService voiceService,
            AssistantRuntimeHealthService runtimeHealthService,
            CurrentUser currentUser
    ) {
        this.service =
                service;

        this.voiceService =
                voiceService;

        this.runtimeHealthService =
                runtimeHealthService;

        this.currentUser =
                currentUser;
    }

    @GetMapping("/health")
    public AssistantRuntimeHealthResponse health(
            @PathVariable UUID projectId
    ) {
        return runtimeHealthService.health(
                currentUser.id(),
                projectId
        );
    }

    @PostMapping("/plan")
    public AssistantPlanResponse plan(
            @PathVariable UUID projectId,
            @Valid @RequestBody AssistantPlanRequest request
    ) {
        return service.plan(
                currentUser.id(),
                projectId,
                request.text()
        );
    }

    @PostMapping(
            value = "/voice",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AssistantPlanResponse voice(
            @PathVariable UUID projectId,
            @RequestParam("audio") MultipartFile audio
    ) {
        return voiceService.plan(
                currentUser.id(),
                projectId,
                audio
        );
    }

    @ExceptionHandler(
            AssistantPlanningException.class
    )
    @ResponseStatus(
            HttpStatus.SERVICE_UNAVAILABLE
    )
    public Map<String, Object> handle(
            AssistantPlanningException exception
    ) {
        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "error",
                "ASSISTANT_PLANNING_ERROR"
        );

        response.put(
                "message",
                exception.getMessage()
        );

        if (exception.stage() != null) {
            response.put(
                    "stage",
                    exception.stage()
                            .name()
            );
        }

        if (
                exception.source() != null
                        && !exception.source()
                                .isBlank()
        ) {
            response.put(
                    "source",
                    exception.source()
            );
        }

        if (
                exception.transcript() != null
                        && !exception.transcript()
                                .isBlank()
        ) {
            response.put(
                    "transcript",
                    exception.transcript()
            );
        }

        if (
                exception.attemptedPlan()
                        != null
        ) {
            response.put(
                    "attemptedPlan",
                    exception.attemptedPlan()
            );
        }

        return response;
    }
}