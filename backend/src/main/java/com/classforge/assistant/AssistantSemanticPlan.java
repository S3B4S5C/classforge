package com.classforge.assistant;

import java.util.List;

public record AssistantSemanticPlan(
        String summary,
        List<AssistantPlanAction> actions
) {
}