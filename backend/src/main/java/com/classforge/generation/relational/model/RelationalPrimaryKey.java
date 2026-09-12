package com.classforge.generation.relational.model;

import java.util.List;

public record RelationalPrimaryKey(List<String> columnNames) {
    public RelationalPrimaryKey {
        columnNames = List.copyOf(columnNames == null ? List.of() : columnNames);
        if (columnNames.isEmpty()) throw new IllegalArgumentException("Primary key columns are required");
    }
}
