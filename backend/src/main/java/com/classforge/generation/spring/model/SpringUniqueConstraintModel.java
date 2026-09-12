package com.classforge.generation.spring.model;

import java.util.List;

public record SpringUniqueConstraintModel(List<String> columnNames) {
    public SpringUniqueConstraintModel { columnNames = List.copyOf(columnNames == null ? List.of() : columnNames); }
}
