package com.classforge.assistant;

public enum AssistantPlanningStage {
    STT,
    LLM,
    TOOL_RESOLUTION,
    GROUNDING,
    NORMALIZATION,
    RESOLUTION,
    PREVIEW
}