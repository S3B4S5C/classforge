package com.classforge.assistant;

import java.time.Instant;

public record AssistantRuntimeHealthResponse(
        boolean readyForText,
        boolean readyForVoice,
        AssistantRuntimeStatus llama,
        AssistantRuntimeStatus whisper,
        Instant checkedAt
) {
}