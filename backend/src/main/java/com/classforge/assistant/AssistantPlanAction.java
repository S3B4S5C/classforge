package com.classforge.assistant;

import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;

import java.util.List;

public record AssistantPlanAction(
        AssistantActionType type,
        String className,
        String newName,
        List<AssistantAttributePlan> attributes,
        String attributeName,
        String newAttributeName,
        UmlDataType dataType,
        String customTypeName,
        UmlVisibility visibility,
        Boolean nullable,
        Boolean identifier,
        String sourceClassName,
        String targetClassName,
        UmlRelationshipType relationshipType,
        Integer sourceLower,
        Integer sourceUpper,
        Integer targetLower,
        Integer targetUpper
) {
    public List<AssistantAttributePlan> safeAttributes() {
        return attributes == null
                ? List.of()
                : List.copyOf(attributes);
    }
}