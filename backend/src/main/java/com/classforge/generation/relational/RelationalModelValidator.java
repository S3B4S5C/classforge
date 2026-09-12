package com.classforge.generation.relational;

import com.classforge.generation.relational.model.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RelationalModelValidator {
    public void validate(RelationalModel model) {
        Objects.requireNonNull(model, "model is required");
        Map<String, RelationalTable> tables = new HashMap<>();
        Set<UUID> relationIds = new HashSet<>();
        for (RelationalTable table : model.tables()) {
            require(table != null && table.name() != null && !table.name().isBlank(), "Table name is required");
            require(tables.putIfAbsent(table.name(), table) == null, "Duplicate table " + table.name());
            Map<String, RelationalColumn> columns = new HashMap<>();
            for (RelationalColumn column : table.columns()) {
                require(column != null && column.name() != null && !column.name().isBlank(), "Column name is required");
                require(columns.putIfAbsent(column.name(), column) == null, "Duplicate column " + column.name());
            }
            require(table.primaryKey() != null, "Primary key is required for " + table.name());
            require(!table.primaryKey().columnNames().isEmpty(), "Primary key is empty for " + table.name());
            for (String name : table.primaryKey().columnNames()) {
                RelationalColumn column = columns.get(name);
                require(column != null, "Primary key column is missing: " + name);
                require(!column.nullable(), "Primary key column is nullable: " + name);
            }
        }
        for (RelationalTable table : model.tables()) {
            Set<String> columns = table.columns().stream().map(RelationalColumn::name).collect(java.util.stream.Collectors.toSet());
            for (RelationalForeignKey foreignKey : table.foreignKeys()) {
                require(columns.containsAll(foreignKey.columnNames()), "Foreign key local column is missing");
                RelationalTable target = tables.get(foreignKey.referencedTableName());
                require(target != null, "Foreign key target table is missing");
                require(foreignKey.columnNames().size() == foreignKey.referencedColumnNames().size(), "Foreign key arity differs");
                require(target.primaryKey().columnNames().equals(foreignKey.referencedColumnNames()), "Foreign key must reference target primary key");
            }
            for (RelationalUniqueConstraint unique : table.uniqueConstraints()) require(columns.containsAll(unique.columnNames()), "Unique column is missing");
            for (RelationalIndex index : table.indexes()) require(columns.containsAll(index.columnNames()), "Index column is missing");
        }
        for (RelationalRelation relation : model.relations()) {
            require(relationIds.add(relation.sourceRelationshipId()), "Duplicate relation source id");
            require(tables.containsKey(relation.sourceTableName()) && tables.containsKey(relation.targetTableName()), "Relation table is missing");
            if (relation.storage() == RelationalRelationStorage.JOIN_TABLE) require(relation.joinTableName() != null && tables.containsKey(relation.joinTableName()), "Join table is missing");
            if (relation.storage() != RelationalRelationStorage.JOIN_TABLE) require(relation.owningTableName() != null && tables.containsKey(relation.owningTableName()), "Owning table is missing");
        }
    }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
