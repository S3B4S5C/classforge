package com.classforge.projectassistant;

public interface ProjectAssistantConversationGateway {
    AssistantConversationResponse respond(ProjectAssistantContext context, String userMessage, AssistantMode mode);
}
