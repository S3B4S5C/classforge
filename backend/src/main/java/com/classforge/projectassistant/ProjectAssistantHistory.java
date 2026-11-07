package com.classforge.projectassistant;

import java.util.List;

public record ProjectAssistantHistory(List<ProjectAiMessage> messages, boolean hasMore) { }
