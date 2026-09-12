package com.classforge.generation.relational.model;

import com.classforge.project.domain.document.UmlRelationshipType;
import java.util.UUID;

public record RelationalRelation(UUID sourceRelationshipId, UmlRelationshipType umlType,
                                 String sourceTableName, String targetTableName,
                                 RelationalCardinality sourceCardinality, RelationalCardinality targetCardinality,
                                 RelationalRelationStorage storage, String owningTableName,
                                 String joinTableName, RelationalReferentialAction onDelete) { }
