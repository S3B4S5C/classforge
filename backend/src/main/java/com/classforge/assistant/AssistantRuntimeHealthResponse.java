package com.classforge.assistant;

import java.time.Instant;

public record AssistantRuntimeHealthResponse(
        boolean readyForText,
        boolean readyForVoice,
        boolean readyForImage,
        AssistantRuntimeStatus llama,
        AssistantRuntimeStatus whisper,
        AssistantRuntimeStatus vision,
        Instant checkedAt
) {
}
