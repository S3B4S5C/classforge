package com.classforge.assistant;

public enum AssistantPlanningStage {
    STT,
    VISION,
    VISION_GROUNDING,
    LLM,
    TOOL_RESOLUTION,
    GROUNDING,
    NORMALIZATION,
    RESOLUTION,
    PREVIEW
}