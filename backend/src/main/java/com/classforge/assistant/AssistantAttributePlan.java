package com.classforge.assistant;

import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlVisibility;

public record AssistantAttributePlan(
        String name,
        UmlDataType dataType,
        String customTypeName,
        UmlVisibility visibility,
        Boolean nullable,
        Boolean identifier,
        AssistantTypeSource typeSource
) {
}