package com.classforge.projectassistant;

public record ProjectAssistantResponse(ProjectAiMessage userMessage, ProjectAiMessage assistantMessage,
                                       long projectRevision) { }
