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
        Integer targetUpper,
        String associationClassName
) {
    // Preserve the old constructor for all pre-existing planner and tool callers.
    public AssistantPlanAction(
            AssistantActionType type, String className, String newName,
            List<AssistantAttributePlan> attributes, String attributeName,
            String newAttributeName, UmlDataType dataType, String customTypeName,
            UmlVisibility visibility, Boolean nullable, Boolean identifier,
            String sourceClassName, String targetClassName,
            UmlRelationshipType relationshipType, Integer sourceLower,
            Integer sourceUpper, Integer targetLower, Integer targetUpper
    ) {
        this(type, className, newName, attributes, attributeName, newAttributeName,
                dataType, customTypeName, visibility, nullable, identifier,
                sourceClassName, targetClassName, relationshipType,
                sourceLower, sourceUpper, targetLower, targetUpper, null);
    }

    public List<AssistantAttributePlan> safeAttributes() {
        return attributes == null
                ? List.of()
                : List.copyOf(attributes);
    }
}