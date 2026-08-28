package com.classforge.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantPlanRequest(
        @NotBlank
        @Size(max = 1000)
        String text
) {
}