package com.classforge.assistant;

public record AssistantRuntimeStatus(
        String name,
        boolean available,
        String state,
        long latencyMs,
        String message
) {
}