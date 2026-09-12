package com.classforge.generation.spring.model;

import java.util.List;

public record SpringIndexModel(List<String> columnNames) {
    public SpringIndexModel { columnNames = List.copyOf(columnNames == null ? List.of() : columnNames); }
}
