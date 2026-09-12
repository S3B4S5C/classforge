package com.classforge.generation.relational.model;

import java.util.List;

public record RelationalIndex(List<String> columnNames) {
    public RelationalIndex { columnNames = List.copyOf(columnNames == null ? List.of() : columnNames); }
}
