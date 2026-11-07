package com.classforge.projectassistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProjectAssistantMessageRequest(@NotBlank @Size(max = 4000) String content,
        AssistantMode mode, @NotNull UUID requestId) {
    public ProjectAssistantMessageRequest { mode = mode == null ? AssistantMode.ASK : mode; }
}
