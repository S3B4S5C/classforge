package com.classforge.projectassistant;

import java.util.List;

public record AssistantConversationResponse(String content, List<AssistantReviewFinding> findings) {
    public AssistantConversationResponse { findings = findings == null ? List.of() : List.copyOf(findings); }
}
