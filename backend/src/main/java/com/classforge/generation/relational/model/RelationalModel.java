package com.classforge.generation.relational.model;

import java.util.List;

public record RelationalModel(String schemaVersion, List<RelationalTable> tables, List<RelationalRelation> relations) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";
    public RelationalModel {
        tables = List.copyOf(tables == null ? List.of() : tables);
        relations = List.copyOf(relations == null ? List.of() : relations);
    }
}
