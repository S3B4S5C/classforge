package com.classforge.generation.relational.model;

import java.util.List;

public record RelationalTable(RelationalTableOrigin origin, String logicalName, String name,
                              List<RelationalColumn> columns, RelationalPrimaryKey primaryKey,
                              List<RelationalForeignKey> foreignKeys,
                              List<RelationalUniqueConstraint> uniqueConstraints,
                              List<RelationalIndex> indexes) {
    public RelationalTable {
        columns = List.copyOf(columns == null ? List.of() : columns);
        foreignKeys = List.copyOf(foreignKeys == null ? List.of() : foreignKeys);
        uniqueConstraints = List.copyOf(uniqueConstraints == null ? List.of() : uniqueConstraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
    }
}
