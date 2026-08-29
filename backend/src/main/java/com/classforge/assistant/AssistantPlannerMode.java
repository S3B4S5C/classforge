package com.classforge.assistant;

import java.util.Locale;

public enum AssistantPlannerMode {
    LEGACY,
    TOOLS,
    COMPARE;

    public static AssistantPlannerMode parse(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY;
        }

        try {
            return AssistantPlannerMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Modo de planner desconocido '" + value + "'. Usa legacy, tools o compare.",
                    exception
            );
        }
    }
}
