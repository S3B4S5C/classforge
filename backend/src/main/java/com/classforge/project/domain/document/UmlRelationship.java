package com.classforge.project.domain.document;

import java.util.UUID;

public record UmlRelationship(
        UUID id,
        UUID sourceClassId,
        UUID targetClassId,
        UmlRelationshipType type,
        Multiplicity sourceMultiplicity,
        Multiplicity targetMultiplicity,
        UUID associationClassId
) {
    /**
     * Backward-compatible UML association. When present, associationClassId
     * points to an existing UmlClass containing attributes of the relationship.
     * The associated class is NOT an extra relationship endpoint.
     */
    public UmlRelationship(
            UUID id, UUID sourceClassId, UUID targetClassId,
            UmlRelationshipType type, Multiplicity sourceMultiplicity,
            Multiplicity targetMultiplicity
    ) {
        this(id, sourceClassId, targetClassId, type,
                sourceMultiplicity, targetMultiplicity, null);
    }
}