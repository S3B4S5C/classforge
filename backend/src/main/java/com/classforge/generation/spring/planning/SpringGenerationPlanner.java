package com.classforge.generation.spring.planning;

import com.classforge.generation.relational.model.*;
import com.classforge.generation.spring.model.*;
import com.classforge.generation.spring.validation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SpringGenerationPlanner {
    private final SpringJavaNamingStrategy naming;
    private final SpringGenerationModelValidator validator;

    public SpringGenerationPlanner(SpringJavaNamingStrategy naming, SpringGenerationModelValidator validator) { this.naming = naming; this.validator = validator; }

    public SpringGenerationModel plan(RelationalModel relationalModel, SpringGenerationConfig config) {
        Objects.requireNonNull(relationalModel, "relationalModel is required");
        Objects.requireNonNull(config, "config is required");
        List<SpringGenerationDiagnostic> diagnostics = new ArrayList<>();
        Map<String, RelationalTable> tables = new HashMap<>();
        for (RelationalTable table : relationalModel.tables()) if (table != null && table.name() != null) tables.put(table.name(), table);
        List<RelationalTable> entityTables = relationalModel.tables().stream()
                .filter(t -> t != null && t.origin() != null && t.origin().type() == RelationalTableOriginType.UML_CLASS)
                .sorted(Comparator.comparing(RelationalTable::name).thenComparing(t -> String.valueOf(t.origin().umlElementId()))).toList();
        Map<String, String> classNames = new HashMap<>();
        Map<String, String> tableByClass = new HashMap<>();
        for (RelationalTable table : entityTables) {
            if (table.origin().umlElementId() == null) diag(diagnostics, SpringGenerationDiagnosticCode.ENTITY_TABLE_NOT_FOUND, null, "tables", "UML-class table has no source class id.");
            String className = naming.entityClassName(table.logicalName());
            String previous = tableByClass.putIfAbsent(className, table.name());
            if (previous != null) diag(diagnostics, SpringGenerationDiagnosticCode.JAVA_TYPE_NAME_COLLISION, table.origin().umlElementId(), "tables.logicalName", "Tables map to the same Java entity type: " + className);
            classNames.put(table.name(), className);
        }
        Map<String, RelationalRelation> parentRelations = inheritanceRelations(relationalModel.relations(), tables, classNames, diagnostics);
        detectInheritanceCycles(parentRelations, diagnostics);
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);

        Map<String, SpringEntityIdModel> rootIds = new HashMap<>();
        for (RelationalTable table : entityTables) if (!parentRelations.containsKey(table.name())) {
            SpringEntityIdModel id = declaredId(table, diagnostics);
            rootIds.put(table.name(), id);
        }
        for (RelationalTable table : entityTables) if (!parentRelations.containsKey(table.name()) && rootIds.get(table.name()) != null
                && rootIds.get(table.name()).kind() == SpringIdKind.COMPOSITE) {
            String idClassName = rootIds.get(table.name()).idClassName();
            String conflictingTable = tableByClass.get(idClassName);
            if (conflictingTable != null && !conflictingTable.equals(table.name())) diag(diagnostics, SpringGenerationDiagnosticCode.JAVA_TYPE_NAME_COLLISION, table.origin().umlElementId(), "entities.id", "Generated id class collides with entity class: " + idClassName);
        }
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);

        Map<String, SpringEntityIdModel> ids = new HashMap<>();
        Map<String, SpringInheritanceModel> inheritances = new HashMap<>();
        for (RelationalTable table : entityTables) {
            RelationalRelation parent = parentRelations.get(table.name());
            if (parent == null) {
                boolean hasChildren = parentRelations.values().stream().anyMatch(r -> r.targetTableName().equals(table.name()));
                ids.put(table.name(), rootIds.get(table.name()));
                inheritances.put(table.name(), new SpringInheritanceModel(hasChildren ? SpringInheritanceKind.JOINED_ROOT : SpringInheritanceKind.NONE, null, List.of()));
            } else {
                String root = rootTable(table.name(), parentRelations);
                SpringEntityIdModel rootId = rootIds.get(root);
                List<SpringJoinColumnModel> joins = inheritanceJoins(table, tables.get(parent.targetTableName()), parent, diagnostics);
                ids.put(table.name(), new SpringEntityIdModel(rootId.kind(), rootId.javaType(), rootId.idClassName(), rootId.fields(), false));
                inheritances.put(table.name(), new SpringInheritanceModel(SpringInheritanceKind.JOINED_SUBCLASS, classNames.get(parent.targetTableName()), joins));
            }
        }
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);

        Map<String, List<SpringDirectRelationModel>> direct = new HashMap<>();
        Map<String, List<SpringManyToManyRelationModel>> many = new HashMap<>();
        for (String tableName : classNames.keySet()) { direct.put(tableName, new ArrayList<>()); many.put(tableName, new ArrayList<>()); }
        for (RelationalRelation relation : relationalModel.relations().stream().sorted(relationOrder()).toList()) {
            if (relation.storage() == RelationalRelationStorage.FOREIGN_KEY) planDirect(relation, tables, classNames, direct, diagnostics);
            if (relation.storage() == RelationalRelationStorage.JOIN_TABLE) planManyToMany(relation, tables, classNames, many, diagnostics);
        }
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);

        List<SpringEntityModel> entities = new ArrayList<>();
        for (RelationalTable table : entityTables) {
            List<SpringScalarFieldModel> fields = scalarFields(table, parentRelations.containsKey(table.name()), diagnostics);
            List<SpringDirectRelationModel> directRelations = sortedDirect(direct.get(table.name()));
            List<SpringManyToManyRelationModel> manyRelations = sortedMany(many.get(table.name()));
            collisionCheck(table, fields, directRelations, manyRelations, diagnostics);
            entities.add(new SpringEntityModel(table.origin().umlElementId(), table.logicalName(), classNames.get(table.name()), table.name(), inheritances.get(table.name()), ids.get(table.name()), fields, directRelations, manyRelations, constraints(table.uniqueConstraints()), indexes(table.indexes())));
        }
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);
        List<SpringRepositoryModel> repositories = entities.stream().map(entity -> new SpringRepositoryModel(naming.repositoryName(entity.className()), entity.className(), entity.id().typeSimpleName(), entity.id().typeQualifiedName(), entity.id().kind() == SpringIdKind.COMPOSITE)).toList();
        Set<String> repositoryNames = new HashSet<>();
        for (SpringRepositoryModel repository : repositories) if (!repositoryNames.add(repository.interfaceName())) diag(diagnostics, SpringGenerationDiagnosticCode.JAVA_REPOSITORY_NAME_COLLISION, null, "repositories", "Repository name collision: " + repository.interfaceName());
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);
        SpringGenerationModel model = new SpringGenerationModel(SpringGenerationModel.GENERATOR_SCHEMA_VERSION, config.artifactName(), config.basePackage(), naming.applicationClassName(config.artifactName()), SpringGenerationModel.JAVA_VERSION, SpringGenerationModel.SPRING_BOOT_VERSION, SpringGenerationModel.GRADLE_VERSION, entities, repositories);
        validator.validate(model);
        return model;
    }

    private Map<String, RelationalRelation> inheritanceRelations(List<RelationalRelation> relations, Map<String, RelationalTable> tables, Map<String, String> classNames, List<SpringGenerationDiagnostic> diagnostics) {
        Map<String, RelationalRelation> result = new HashMap<>();
        for (RelationalRelation relation : relations) if (relation.storage() == RelationalRelationStorage.JOINED_INHERITANCE) {
            if (!tables.containsKey(relation.sourceTableName()) || !classNames.containsKey(relation.sourceTableName()) || !classNames.containsKey(relation.targetTableName())) {
                diag(diagnostics, SpringGenerationDiagnosticCode.INHERITANCE_PARENT_NOT_FOUND, relation.sourceRelationshipId(), "relations", "JOINED inheritance table is missing."); continue;
            }
            if (result.putIfAbsent(relation.sourceTableName(), relation) != null) diag(diagnostics, SpringGenerationDiagnosticCode.INHERITANCE_PARENT_NOT_FOUND, relation.sourceRelationshipId(), "relations", "Subclass has more than one parent.");
        }
        return result;
    }
    private void detectInheritanceCycles(Map<String, RelationalRelation> parents, List<SpringGenerationDiagnostic> diagnostics) {
        for (String child : parents.keySet()) { Set<String> visited = new HashSet<>(); String current = child; while (parents.containsKey(current)) { if (!visited.add(current)) { diag(diagnostics, SpringGenerationDiagnosticCode.INHERITANCE_CYCLE, parents.get(child).sourceRelationshipId(), "relations", "JOINED inheritance contains a cycle."); break; } current = parents.get(current).targetTableName(); } }
    }
    private String rootTable(String table, Map<String, RelationalRelation> parents) { String current = table; while (parents.containsKey(current)) current = parents.get(current).targetTableName(); return current; }
    private SpringEntityIdModel declaredId(RelationalTable table, List<SpringGenerationDiagnostic> diagnostics) {
        List<SpringIdFieldModel> fields = new ArrayList<>();
        for (String columnName : table.primaryKey().columnNames()) {
            RelationalColumn column = table.columns().stream().filter(c -> c.name().equals(columnName) && c.origin() == RelationalColumnOrigin.ATTRIBUTE).findFirst().orElse(null);
            if (column == null) { diag(diagnostics, SpringGenerationDiagnosticCode.ENTITY_PRIMARY_KEY_INVALID, table.origin().umlElementId(), "tables.primaryKey", "Root primary key column must be an ATTRIBUTE: " + columnName); continue; }
            fields.add(new SpringIdFieldModel(column.sourceElementId(), naming.fieldName(column.logicalName()), column.name(), SpringJavaType.from(column.dataType())));
        }
        if (fields.isEmpty() || fields.size() != table.primaryKey().columnNames().size()) return null;
        return fields.size() == 1 ? new SpringEntityIdModel(SpringIdKind.SIMPLE, fields.getFirst().javaType(), null, fields, true) : new SpringEntityIdModel(SpringIdKind.COMPOSITE, null, naming.idClassName(naming.entityClassName(table.logicalName())), fields, true);
    }
    private List<SpringJoinColumnModel> inheritanceJoins(RelationalTable child, RelationalTable parent, RelationalRelation relation, List<SpringGenerationDiagnostic> diagnostics) {
        List<RelationalForeignKey> matches = child.foreignKeys().stream().filter(fk -> Objects.equals(fk.sourceRelationshipId(), relation.sourceRelationshipId())).toList();
        if (matches.size() != 1 || parent == null) { diag(diagnostics, SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID, relation.sourceRelationshipId(), "relations", "JOINED inheritance FK is missing."); return List.of(); }
        RelationalForeignKey fk = matches.getFirst();
        if (!Objects.equals(fk.referencedTableName(), relation.targetTableName())
                || !fk.columnNames().equals(child.primaryKey().columnNames())
                || !fk.referencedColumnNames().equals(parent.primaryKey().columnNames())
                || fk.columnNames().size() != fk.referencedColumnNames().size()) {
            diag(diagnostics, SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID, relation.sourceRelationshipId(), "relations", "JOINED inheritance FK must map the child primary key to the immediate parent primary key.");
            return List.of();
        }
        return joinColumns(fk, child, diagnostics, relation.sourceRelationshipId(), SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID);
    }
    private List<SpringScalarFieldModel> scalarFields(RelationalTable table, boolean subclass, List<SpringGenerationDiagnostic> diagnostics) {
        List<SpringScalarFieldModel> fields = new ArrayList<>();
        for (RelationalColumn column : table.columns()) if (column.origin() == RelationalColumnOrigin.ATTRIBUTE) {
            String fieldName = naming.fieldName(column.logicalName());
            fields.add(new SpringScalarFieldModel(column.sourceElementId(), column.logicalName(), fieldName, column.name(), SpringJavaType.from(column.dataType()), column.nullable(), !subclass && table.primaryKey().columnNames().contains(column.name())));
        }
        return fields.stream().sorted(Comparator.comparing(SpringScalarFieldModel::fieldName).thenComparing(SpringScalarFieldModel::columnName).thenComparing(f -> f.sourceAttributeId().toString())).toList();
    }
    private void planDirect(RelationalRelation relation, Map<String, RelationalTable> tables, Map<String, String> classes, Map<String, List<SpringDirectRelationModel>> result, List<SpringGenerationDiagnostic> diagnostics) {
        RelationalTable owner = tables.get(relation.owningTableName());
        if (owner == null || !classes.containsKey(relation.owningTableName())) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_OWNER_NOT_FOUND, relation.sourceRelationshipId(), "relations.owningTableName", "Direct relation owner entity is missing."); return; }
        List<RelationalForeignKey> matches = owner.foreignKeys().stream().filter(fk -> Objects.equals(fk.sourceRelationshipId(), relation.sourceRelationshipId())).toList();
        if (matches.isEmpty()) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_FOREIGN_KEY_NOT_FOUND, relation.sourceRelationshipId(), "relations", "Direct relation FK is missing."); return; }
        if (matches.size() > 1) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_FOREIGN_KEY_AMBIGUOUS, relation.sourceRelationshipId(), "relations", "Direct relation FK is ambiguous."); return; }
        RelationalForeignKey fk = matches.getFirst(); String targetName = classes.get(fk.referencedTableName());
        if (targetName == null) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_TARGET_NOT_FOUND, relation.sourceRelationshipId(), "foreignKeys.referencedTableName", "Direct relation target entity is missing."); return; }
        List<SpringJoinColumnModel> joins = joinColumns(fk, owner, diagnostics, relation.sourceRelationshipId(), SpringGenerationDiagnosticCode.RELATION_FOREIGN_KEY_NOT_FOUND);
        boolean allNullable = joins.stream().allMatch(SpringJoinColumnModel::nullable), noneNullable = joins.stream().noneMatch(SpringJoinColumnModel::nullable);
        if (!allNullable && !noneNullable) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_NULLABILITY_INCONSISTENT, relation.sourceRelationshipId(), "foreignKeys", "Composite FK has mixed nullability."); return; }
        boolean unique = owner.uniqueConstraints().stream().anyMatch(constraint -> normalized(constraint.columnNames()).equals(normalized(fk.columnNames())));
        result.get(owner.name()).add(new SpringDirectRelationModel(relation.sourceRelationshipId(), relation.umlType(), unique ? SpringDirectRelationKind.ONE_TO_ONE : SpringDirectRelationKind.MANY_TO_ONE, naming.fieldName(targetName), targetName, fk.referencedTableName(), joins, allNullable, fk.onDelete() == RelationalReferentialAction.CASCADE));
    }
    private void planManyToMany(RelationalRelation relation, Map<String, RelationalTable> tables, Map<String, String> classes, Map<String, List<SpringManyToManyRelationModel>> result, List<SpringGenerationDiagnostic> diagnostics) {
        RelationalTable join = tables.get(relation.joinTableName());
        if (join == null || join.origin() == null || join.origin().type() != RelationalTableOriginType.JOIN_RELATIONSHIP) { diag(diagnostics, SpringGenerationDiagnosticCode.JOIN_TABLE_NOT_FOUND, relation.sourceRelationshipId(), "relations.joinTableName", "JOIN_TABLE relation has no join table."); return; }
        String ownerTable = relation.sourceTableName(), targetTable = relation.targetTableName();
        if (!classes.containsKey(ownerTable) || !classes.containsKey(targetTable)) { diag(diagnostics, SpringGenerationDiagnosticCode.RELATION_TARGET_NOT_FOUND, relation.sourceRelationshipId(), "relations", "Many-to-many entity target is missing."); return; }
        List<RelationalForeignKey> ownerFks = join.foreignKeys().stream().filter(fk -> Objects.equals(fk.sourceRelationshipId(), relation.sourceRelationshipId()) && ownerTable.equals(fk.referencedTableName())).toList();
        List<RelationalForeignKey> targetFks = join.foreignKeys().stream().filter(fk -> Objects.equals(fk.sourceRelationshipId(), relation.sourceRelationshipId()) && targetTable.equals(fk.referencedTableName())).toList();
        if (join.foreignKeys().size() != 2 || join.foreignKeys().stream().anyMatch(fk -> !Objects.equals(fk.sourceRelationshipId(), relation.sourceRelationshipId())) || ownerFks.size() != 1 || targetFks.size() != 1) { diag(diagnostics, SpringGenerationDiagnosticCode.JOIN_TABLE_FOREIGN_KEYS_INVALID, relation.sourceRelationshipId(), "joinTable.foreignKeys", "Join table must have exactly one FK to each relation entity."); return; }
        List<SpringJoinColumnModel> joins = joinColumns(ownerFks.getFirst(), join, diagnostics, relation.sourceRelationshipId(), SpringGenerationDiagnosticCode.JOIN_TABLE_FOREIGN_KEYS_INVALID);
        List<SpringJoinColumnModel> inverse = joinColumns(targetFks.getFirst(), join, diagnostics, relation.sourceRelationshipId(), SpringGenerationDiagnosticCode.JOIN_TABLE_FOREIGN_KEYS_INVALID);
        result.get(ownerTable).add(new SpringManyToManyRelationModel(relation.sourceRelationshipId(), relation.umlType(), naming.fieldName(classes.get(targetTable)) + "Set", classes.get(targetTable), join.name(), joins, inverse));
    }
    private List<SpringJoinColumnModel> joinColumns(RelationalForeignKey fk, RelationalTable table, List<SpringGenerationDiagnostic> diagnostics, UUID id, SpringGenerationDiagnosticCode code) {
        if (fk.columnNames().isEmpty() || fk.columnNames().size() != fk.referencedColumnNames().size()) { diag(diagnostics, code, id, "foreignKeys", "Foreign-key columns are missing or have unequal arity."); return List.of(); }
        List<SpringJoinColumnModel> result = new ArrayList<>();
        for (int index = 0; index < fk.columnNames().size(); index++) {
            String localName = fk.columnNames().get(index);
            RelationalColumn column = table.columns().stream().filter(c -> c.name().equals(localName)).findFirst().orElse(null);
            if (column == null) { diag(diagnostics, code, id, "foreignKeys.columnNames", "Foreign-key local column is missing."); return List.of(); }
            result.add(new SpringJoinColumnModel(column.name(), fk.referencedColumnNames().get(index), column.nullable()));
        }
        return List.copyOf(result);
    }
    private void collisionCheck(RelationalTable table, List<SpringScalarFieldModel> scalars, List<SpringDirectRelationModel> direct, List<SpringManyToManyRelationModel> many, List<SpringGenerationDiagnostic> diagnostics) { Set<String> names = new HashSet<>(); for (SpringScalarFieldModel field : scalars) fieldCollision(names, field.fieldName(), table, diagnostics); for (SpringDirectRelationModel relation : direct) fieldCollision(names, relation.fieldName(), table, diagnostics); for (SpringManyToManyRelationModel relation : many) fieldCollision(names, relation.fieldName(), table, diagnostics); }
    private void fieldCollision(Set<String> names, String name, RelationalTable table, List<SpringGenerationDiagnostic> diagnostics) { if (!names.add(name)) diag(diagnostics, SpringGenerationDiagnosticCode.JAVA_FIELD_NAME_COLLISION, table.origin().umlElementId(), "entities.fields", "Generated field name collision: " + name); }
    private List<SpringUniqueConstraintModel> constraints(List<RelationalUniqueConstraint> values) { return values.stream().map(v -> new SpringUniqueConstraintModel(v.columnNames())).sorted(Comparator.comparing(v -> String.join("\u0000", v.columnNames()))).toList(); }
    private List<SpringIndexModel> indexes(List<RelationalIndex> values) { return values.stream().map(v -> new SpringIndexModel(v.columnNames())).sorted(Comparator.comparing(v -> String.join("\u0000", v.columnNames()))).toList(); }
    private List<SpringDirectRelationModel> sortedDirect(List<SpringDirectRelationModel> values) { return values.stream().sorted(Comparator.comparing(SpringDirectRelationModel::fieldName).thenComparing(SpringDirectRelationModel::targetEntityClassName).thenComparing(r -> r.sourceRelationshipId().toString())).toList(); }
    private List<SpringManyToManyRelationModel> sortedMany(List<SpringManyToManyRelationModel> values) { return values.stream().sorted(Comparator.comparing(SpringManyToManyRelationModel::fieldName).thenComparing(SpringManyToManyRelationModel::joinTableName).thenComparing(r -> r.sourceRelationshipId().toString())).toList(); }
    private Comparator<RelationalRelation> relationOrder() { return Comparator.comparing(RelationalRelation::sourceTableName).thenComparing(RelationalRelation::targetTableName).thenComparing(r -> r.storage().name()).thenComparing(r -> r.sourceRelationshipId().toString()); }
    private List<String> normalized(List<String> values) { return values.stream().sorted().toList(); }
    private void diag(List<SpringGenerationDiagnostic> diagnostics, SpringGenerationDiagnosticCode code, UUID sourceId, String path, String message) { diagnostics.add(new SpringGenerationDiagnostic(code, sourceId, path, message)); }
}
