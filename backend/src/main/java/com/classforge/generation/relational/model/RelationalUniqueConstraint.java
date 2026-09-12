package com.classforge.generation.relational.model;

import java.util.List;

public record RelationalUniqueConstraint(List<String> columnNames) {
    public RelationalUniqueConstraint { columnNames = List.copyOf(columnNames == null ? List.of() : columnNames); }
}
