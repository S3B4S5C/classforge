package com.classforge.collaboration.protocol;

import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlRelationship;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UmlCommandPayload(
        UUID commandId,
        Instant issuedAt,
        UmlCommandType type,
        UUID classId,
        String name,
        UmlClass umlClass,
        DiagramNodeLayout layout,
        UmlAttribute attribute,
        UUID attributeId,
        UmlRelationship relationship,
        UUID relationshipId,
        List<UmlRelationship> relationships
) {
    public UmlCommandPayload(
            UUID commandId,
            Instant issuedAt,
            UmlCommandType type,
            UUID classId,
            String name,
            UmlClass umlClass,
            DiagramNodeLayout layout,
            UmlAttribute attribute,
            UUID attributeId,
            UmlRelationship relationship,
            UUID relationshipId
    ) {
        this(
                commandId,
                issuedAt,
                type,
                classId,
                name,
                umlClass,
                layout,
                attribute,
                attributeId,
                relationship,
                relationshipId,
                List.of()
        );
    }

    public List<UmlRelationship> safeRelationships() {
        return relationships == null
                ? List.of()
                : List.copyOf(relationships);
    }
}