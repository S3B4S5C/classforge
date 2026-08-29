package com.classforge.assistant.tools;

import java.util.List;

/** One already accepted planning round sent back to llama.cpp as tool-call history. */
public record AssistantToolConversationTurn(
        List<AssistantToolInvocation> invocations,
        List<String> results
) {
    public AssistantToolConversationTurn {
        invocations = invocations == null ? List.of() : List.copyOf(invocations);
        results = results == null ? List.of() : List.copyOf(results);
        if (invocations.size() != results.size()) {
            throw new IllegalArgumentException("Cada tool invocation debe tener exactamente un resultado.");
        }
    }
}
