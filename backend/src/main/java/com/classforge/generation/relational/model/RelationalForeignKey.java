package com.classforge.generation.relational.model;

import java.util.List;
import java.util.UUID;

public record RelationalForeignKey(UUID sourceRelationshipId, List<String> columnNames,
                                   String referencedTableName, List<String> referencedColumnNames,
                                   RelationalReferentialAction onDelete) {
    public RelationalForeignKey {
        columnNames = List.copyOf(columnNames == null ? List.of() : columnNames);
        referencedColumnNames = List.copyOf(referencedColumnNames == null ? List.of() : referencedColumnNames);
    }
}
