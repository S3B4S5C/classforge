package com.classforge.assistant.tools;

import com.classforge.project.domain.document.UmlRelationshipType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssistantToolCatalog(
        List<AssistantToolDefinition> definitions,
        Map<String, UUID> classIdsByName,
        Map<String, AttributeReference> attributesByLabel,
        Map<String, RelationshipReference> relationshipsByLabel
) {
    public record AttributeReference(
            String label,
            UUID classId,
            String className,
            UUID attributeId,
            String attributeName
    ) {
    }

    public record RelationshipReference(
            String label,
            UUID relationshipId,
            String sourceClassName,
            String targetClassName,
            UmlRelationshipType type
    ) {
    }
}
