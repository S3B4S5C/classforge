package com.classforge.generation.spring.model;

import com.classforge.project.domain.document.UmlRelationshipType;
import java.util.List;
import java.util.UUID;

public record SpringDirectRelationModel(UUID sourceRelationshipId, UmlRelationshipType umlType,
                                        SpringDirectRelationKind kind, String fieldName,
                                        String targetEntityClassName, String targetTableName,
                                        List<SpringJoinColumnModel> joinColumns, boolean optional,
                                        boolean onDeleteCascade) {
    public SpringDirectRelationModel { joinColumns = List.copyOf(joinColumns == null ? List.of() : joinColumns); }
}
