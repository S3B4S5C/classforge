package com.classforge.project.domain.document;

import java.util.UUID;

public record UmlRelationship(
        UUID id,
        UUID sourceClassId,
        UUID targetClassId,
        UmlRelationshipType type,
        Multiplicity sourceMultiplicity,
        Multiplicity targetMultiplicity
) {
}