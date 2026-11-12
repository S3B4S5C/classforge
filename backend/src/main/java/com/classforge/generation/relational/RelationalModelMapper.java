package com.classforge.generation.relational;

import com.classforge.generation.relational.model.*;
import com.classforge.project.domain.document.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class RelationalModelMapper {
    private final RelationalNamingStrategy naming;
    private final RelationalModelValidator validator;
    public RelationalModelMapper(RelationalNamingStrategy naming, RelationalModelValidator validator) { this.naming = naming; this.validator = validator; }

    public RelationalModel map(UmlModel umlModel) {
        Objects.requireNonNull(umlModel, "umlModel is required");
        List<UmlClass> classes = new ArrayList<>(umlModel.classes());
        List<UmlRelationship> relationships = new ArrayList<>(umlModel.relationships());
        List<RelationalMappingDiagnostic> diagnostics = new ArrayList<>();
        Map<UUID, UmlClass> classById = new HashMap<>();
        for (UmlClass umlClass : classes) if (umlClass != null && umlClass.id() != null) classById.put(umlClass.id(), umlClass);
        Map<UUID, UmlRelationship> parentRelationship = analyzeInheritance(relationships, diagnostics);
        preflight(classes, relationships, classById, parentRelationship, diagnostics);
        if (!diagnostics.isEmpty()) throw new RelationalMappingException(diagnostics);

        Set<UUID> associationClassIds = new HashSet<>();
        for (UmlRelationship relationship : relationships) {
            if (relationship.associationClassId() != null) {
                associationClassIds.add(relationship.associationClassId());
            }
        }

        Map<UUID, TableBuilder> tables = new HashMap<>();
        for (UmlClass umlClass : classes) {
            TableBuilder table = new TableBuilder(new RelationalTableOrigin(RelationalTableOriginType.UML_CLASS, umlClass.id()), umlClass.name(), naming.toSnakeCase(umlClass.name()));
            tables.put(umlClass.id(), table);
            for (UmlAttribute attribute : umlClass.attributes()) table.add(new RelationalColumn(RelationalColumnOrigin.ATTRIBUTE, attribute.id(), attribute.name(), naming.toSnakeCase(attribute.name()), type(attribute.dataType()), attribute.identifier() ? false : attribute.nullable()), diagnostics, umlClass.id());
            if (associationClassIds.contains(umlClass.id())
                    && umlClass.attributes().stream().noneMatch(UmlAttribute::identifier)) {
                // Association classes with no explicit identifier still need a stable,
                // generated primary key in Java/JPA and the relational IR.
                table.add(new RelationalColumn(RelationalColumnOrigin.ATTRIBUTE,
                        derivedId("association-class-id:" + umlClass.id()),
                        "id", "id", RelationalDataType.UUID, false),
                        diagnostics, umlClass.id());
            }
        }
        for (UmlClass umlClass : classes) if (!parentRelationship.containsKey(umlClass.id())) {
            List<String> keys = umlClass.attributes().stream().filter(UmlAttribute::identifier).map(a -> naming.toSnakeCase(a.name())).sorted().toList();
            if (keys.isEmpty() && associationClassIds.contains(umlClass.id())) {
                keys = List.of("id");
            }
            tables.get(umlClass.id()).primaryKey = new RelationalPrimaryKey(keys);
        }
        List<RelationalRelation> relations = new ArrayList<>();
        List<UmlRelationship> generalizations = relationships.stream().filter(r -> r.type() == UmlRelationshipType.GENERALIZATION).sorted(Comparator.<UmlRelationship>comparingInt(r -> inheritanceDepth(r.sourceClassId(), parentRelationship)).thenComparing(r -> r.id().toString())).toList();
        for (UmlRelationship relationship : generalizations) {
            TableBuilder child = tables.get(relationship.sourceClassId()); TableBuilder parent = tables.get(relationship.targetClassId());
            for (String key : parent.primaryKey.columnNames()) {
                RelationalColumn parentColumn = parent.column(key);
                child.add(new RelationalColumn(RelationalColumnOrigin.INHERITED_PRIMARY_KEY, parentColumn.sourceElementId(), parentColumn.logicalName(), key, parentColumn.dataType(), false), diagnostics, relationship.id());
            }
            child.primaryKey = new RelationalPrimaryKey(parent.primaryKey.columnNames());
            child.foreignKeys.add(new RelationalForeignKey(relationship.id(), child.primaryKey.columnNames(), parent.name, parent.primaryKey.columnNames(), RelationalReferentialAction.CASCADE));
            relations.add(new RelationalRelation(relationship.id(), relationship.type(), child.name, parent.name, null, null, RelationalRelationStorage.JOINED_INHERITANCE, child.name, null, RelationalReferentialAction.CASCADE));
        }
        for (UmlRelationship relationship : relationships) if (relationship.type() != UmlRelationshipType.GENERALIZATION) mapRelationship(relationship, tables, relations, diagnostics);
        if (!diagnostics.isEmpty()) throw new RelationalMappingException(diagnostics);
        List<RelationalTable> resultTables = tables.values().stream().map(TableBuilder::build).sorted(tableOrder()).toList();
        List<RelationalRelation> resultRelations = relations.stream().sorted(Comparator.comparing(RelationalRelation::sourceTableName).thenComparing(RelationalRelation::targetTableName).thenComparing(r -> r.umlType().name()).thenComparing(r -> r.sourceRelationshipId().toString())).toList();
        RelationalModel model = new RelationalModel(RelationalModel.CURRENT_SCHEMA_VERSION, resultTables, resultRelations);
        validator.validate(model); return model;
    }

    private Map<UUID, UmlRelationship> analyzeInheritance(List<UmlRelationship> relationships, List<RelationalMappingDiagnostic> ds) {
        Map<UUID, UmlRelationship> parents = new HashMap<>();
        for (UmlRelationship r : relationships) if (r != null && r.type() == UmlRelationshipType.GENERALIZATION) {
            if (parents.putIfAbsent(r.sourceClassId(), r) != null) diag(ds, RelationalMappingDiagnosticCode.MULTIPLE_INHERITANCE_UNSUPPORTED, r.id(), "relationships", "A subclass can have only one direct superclass.");
        }
        for (UUID id : parents.keySet()) { Set<UUID> seen = new HashSet<>(); UUID current = id; while (parents.containsKey(current)) { if (!seen.add(current)) { diag(ds, RelationalMappingDiagnosticCode.GENERALIZATION_CYCLE, id, "relationships", "Generalization contains a cycle."); break; } current = parents.get(current).targetClassId(); } }
        return parents;
    }
    private int inheritanceDepth(UUID classId, Map<UUID, UmlRelationship> parents) {
        int depth = 0; UUID current = classId;
        while (parents.containsKey(current)) { depth++; current = parents.get(current).targetClassId(); }
        return depth;
    }
    private void preflight(List<UmlClass> classes, List<UmlRelationship> rs, Map<UUID, UmlClass> classById, Map<UUID, UmlRelationship> parents, List<RelationalMappingDiagnostic> ds) {
        Map<String, UUID> names = new HashMap<>();
        for (UmlClass c : classes) {
            String physical = naming.toSnakeCase(c.name()); UUID prior = names.putIfAbsent(physical, c.id());
            if (prior != null) diag(ds, RelationalMappingDiagnosticCode.RELATIONAL_TABLE_NAME_COLLISION, c.id(), "classes", "Classes map to the same relational table: " + physical);
            boolean subclass = parents.containsKey(c.id());
            List<UmlAttribute> ids = c.attributes().stream().filter(UmlAttribute::identifier).toList();
            boolean associationClass = rs.stream().anyMatch(r -> r != null && c.id().equals(r.associationClassId()));
            if (!subclass && ids.isEmpty() && !associationClass) diag(ds, RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED, c.id(), "classes", "A root class requires an identifier.");
            if (associationClass && subclass) diag(ds, RelationalMappingDiagnosticCode.ASSOCIATION_CLASS_INVALID, c.id(), "classes", "Association class cannot be an inheritance subclass.");
            if (associationClass && ids.isEmpty() && c.attributes().stream()
                    .anyMatch(a -> naming.toSnakeCase(a.name()).equals("id"))) {
                diag(ds, RelationalMappingDiagnosticCode.RELATIONAL_COLUMN_NAME_COLLISION,
                        c.id(), "classes", "Implicit association-class id would collide with a declared attribute.");
            }
            if (subclass && !ids.isEmpty()) diag(ds, RelationalMappingDiagnosticCode.SUBCLASS_IDENTIFIER_NOT_ALLOWED, c.id(), "classes", "A subclass inherits its identity.");
            for (UmlAttribute a : c.attributes()) if (a.dataType() == UmlDataType.CUSTOM) diag(ds, RelationalMappingDiagnosticCode.CUSTOM_TYPE_UNSUPPORTED, a.id(), "attributes", "CUSTOM is unsupported until enum/value-object semantics are explicit.");
            Set<String> columns = new HashSet<>(); for (UmlAttribute a : c.attributes()) if (!columns.add(naming.toSnakeCase(a.name()))) diag(ds, RelationalMappingDiagnosticCode.RELATIONAL_COLUMN_NAME_COLLISION, a.id(), "attributes", "Attributes map to the same column.");
        }
        for (UmlRelationship r : rs) if (r != null) {
            if (!classById.containsKey(r.sourceClassId())) diag(ds, RelationalMappingDiagnosticCode.RELATIONSHIP_CLASS_NOT_FOUND, r.id(), "relationships.sourceClassId", "Relationship sourceClassId does not reference a known UML class.");
            if (!classById.containsKey(r.targetClassId())) diag(ds, RelationalMappingDiagnosticCode.RELATIONSHIP_CLASS_NOT_FOUND, r.id(), "relationships.targetClassId", "Relationship targetClassId does not reference a known UML class.");
            if (r.associationClassId() != null) {
                if (!classById.containsKey(r.associationClassId())
                        || r.type() != UmlRelationshipType.ASSOCIATION
                        || r.associationClassId().equals(r.sourceClassId())
                        || r.associationClassId().equals(r.targetClassId())
                        || rs.stream().filter(candidate -> candidate != null
                                && r.associationClassId().equals(candidate.associationClassId())).count() > 1) {
                    diag(ds, RelationalMappingDiagnosticCode.ASSOCIATION_CLASS_INVALID,
                            r.id(), "relationships.associationClassId",
                            "Association class must exist, be distinct from endpoints, and belong to exactly one association.");
                }
                if (r.sourceMultiplicity() == null || r.targetMultiplicity() == null
                        || r.sourceMultiplicity().upper() != null
                        || r.targetMultiplicity().upper() != null
                        || r.sourceMultiplicity().lower() > 1
                        || r.targetMultiplicity().lower() > 1) {
                    diag(ds, RelationalMappingDiagnosticCode.ASSOCIATION_CLASS_INVALID,
                            r.id(), "relationships", "Association class requires a many-to-many relation.");
                }
            }
            if (r.type() == UmlRelationshipType.GENERALIZATION) continue;
            if (Objects.equals(r.sourceClassId(), r.targetClassId())) diag(ds, RelationalMappingDiagnosticCode.REFLEXIVE_RELATIONSHIP_UNSUPPORTED, r.id(), "relationships", "Reflexive relationships need role names.");
            RelationalCardinality source = cardinality(r.sourceMultiplicity(), r, ds); RelationalCardinality target = cardinality(r.targetMultiplicity(), r, ds);
            if (r.type() == UmlRelationshipType.COMPOSITION && source != null && (source == RelationalCardinality.ZERO_OR_MANY || source == RelationalCardinality.ONE_OR_MANY)) diag(ds, RelationalMappingDiagnosticCode.COMPOSITION_OWNER_MULTIPLICITY_INVALID, r.id(), "relationships", "A composite part cannot have many owners.");
        }
    }
    private void mapRelationship(UmlRelationship r, Map<UUID, TableBuilder> tables, List<RelationalRelation> relations, List<RelationalMappingDiagnostic> ds) {
        TableBuilder source = tables.get(r.sourceClassId()), target = tables.get(r.targetClassId());
        RelationalCardinality sc = cardinality(r.sourceMultiplicity(), r, ds), tc = cardinality(r.targetMultiplicity(), r, ds); if (sc == null || tc == null || source == null || target == null) return;
        boolean sm = many(sc), tm = many(tc);
        if (r.associationClassId() != null) {
            mapAssociationClass(r, tables, source, target, sc, tc, relations, ds);
            return;
        }
        RelationalReferentialAction action = r.type() == UmlRelationshipType.COMPOSITION ? RelationalReferentialAction.CASCADE : RelationalReferentialAction.NO_ACTION;
        if (r.type() == UmlRelationshipType.COMPOSITION) { addForeignKey(r, target, source, sc == RelationalCardinality.ZERO_OR_ONE, action, false, ds); relations.add(relation(r, source, target, sc, tc, RelationalRelationStorage.FOREIGN_KEY, target.name, null, action)); return; }
        if (sm && tm) { join(r, source, target, sc, tc, action, tables, relations, ds); return; }
        if (sm != tm) { TableBuilder holder = sm ? source : target, referenced = sm ? target : source; RelationalCardinality referencedCard = sm ? tc : sc; addForeignKey(r, holder, referenced, referencedCard == RelationalCardinality.ZERO_OR_ONE, action, false, ds); relations.add(relation(r, source, target, sc, tc, RelationalRelationStorage.FOREIGN_KEY, holder.name, null, action)); return; }
        TableBuilder holder;
        if ((sc == RelationalCardinality.ZERO_OR_ONE) != (tc == RelationalCardinality.ZERO_OR_ONE)) holder = sc == RelationalCardinality.ZERO_OR_ONE ? source : target;
        else if (r.type() == UmlRelationshipType.AGGREGATION) holder = target;
        else holder = source.name.compareTo(target.name) > 0 ? source : target;
        TableBuilder referenced = holder == source ? target : source; RelationalCardinality referencedCard = holder == source ? tc : sc;
        addForeignKey(r, holder, referenced, referencedCard == RelationalCardinality.ZERO_OR_ONE, action, true, ds);
        relations.add(relation(r, source, target, sc, tc, RelationalRelationStorage.FOREIGN_KEY, holder.name, null, action));
    }
    private void mapAssociationClass(
            UmlRelationship relationship, Map<UUID, TableBuilder> tables,
            TableBuilder source, TableBuilder target,
            RelationalCardinality sourceCardinality, RelationalCardinality targetCardinality,
            List<RelationalRelation> relations, List<RelationalMappingDiagnostic> diagnostics
    ) {
        TableBuilder association = tables.get(relationship.associationClassId());
        if (association == null || !many(sourceCardinality) || !many(targetCardinality)) {
            diag(diagnostics, RelationalMappingDiagnosticCode.ASSOCIATION_CLASS_INVALID,
                    relationship.id(), "relationships.associationClassId",
                    "Association class must exist and both endpoints must have many cardinality.");
            return;
        }

        // One persistent entity/table for the association class, with its
        // own UML attributes, two NOT NULL foreign keys and one unique pair.
        UUID sourceFkId = derivedId("association-class:" + relationship.id() + ":source");
        UUID targetFkId = derivedId("association-class:" + relationship.id() + ":target");
        UmlRelationship sourceLink = new UmlRelationship(
                sourceFkId, relationship.associationClassId(),
                relationship.sourceClassId(), UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(), sourceCardinality == RelationalCardinality.ONE_OR_MANY
                        ? new Multiplicity(1, null) : new Multiplicity(0, null));
        UmlRelationship targetLink = new UmlRelationship(
                targetFkId, relationship.associationClassId(),
                relationship.targetClassId(), UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(), targetCardinality == RelationalCardinality.ONE_OR_MANY
                        ? new Multiplicity(1, null) : new Multiplicity(0, null));
        addForeignKey(sourceLink, association, source, false,
                RelationalReferentialAction.NO_ACTION, false, diagnostics);
        addForeignKey(targetLink, association, target, false,
                RelationalReferentialAction.NO_ACTION, false, diagnostics);
        List<String> pair = association.foreignKeys.stream()
                .filter(fk -> fk.sourceRelationshipId().equals(sourceFkId)
                        || fk.sourceRelationshipId().equals(targetFkId))
                .flatMap(fk -> fk.columnNames().stream()).distinct().sorted().toList();
        association.uniques.add(new RelationalUniqueConstraint(pair));
        relations.add(new RelationalRelation(sourceFkId, UmlRelationshipType.ASSOCIATION,
                association.name, source.name, RelationalCardinality.ZERO_OR_MANY,
                RelationalCardinality.EXACTLY_ONE,
                RelationalRelationStorage.FOREIGN_KEY, association.name, null,
                RelationalReferentialAction.NO_ACTION));
        relations.add(new RelationalRelation(targetFkId, UmlRelationshipType.ASSOCIATION,
                association.name, target.name, RelationalCardinality.ZERO_OR_MANY,
                RelationalCardinality.EXACTLY_ONE,
                RelationalRelationStorage.FOREIGN_KEY, association.name, null,
                RelationalReferentialAction.NO_ACTION));
    }

    private UUID derivedId(String seed) {
        return UUID.nameUUIDFromBytes(("classforge:" + seed)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void join(UmlRelationship r, TableBuilder source, TableBuilder target, RelationalCardinality sc, RelationalCardinality tc, RelationalReferentialAction action, Map<UUID, TableBuilder> tables, List<RelationalRelation> relations, List<RelationalMappingDiagnostic> ds) {
        String name = r.type() == UmlRelationshipType.ASSOCIATION ? List.of(source.name, target.name).stream().sorted().reduce((a,b)->a+"_"+b).orElseThrow() : source.name + "_" + target.name;
        if (tables.values().stream().anyMatch(t -> t.name.equals(name))) { diag(ds, RelationalMappingDiagnosticCode.RELATIONAL_TABLE_NAME_COLLISION, r.id(), "relationships", "Join table collides: " + name); return; }
        TableBuilder join = new TableBuilder(new RelationalTableOrigin(RelationalTableOriginType.JOIN_RELATIONSHIP, r.id()), name, name); tables.put(r.id(), join);
        addForeignKey(r, join, source, false, RelationalReferentialAction.NO_ACTION, false, ds); addForeignKey(r, join, target, false, RelationalReferentialAction.NO_ACTION, false, ds);
        join.primaryKey = new RelationalPrimaryKey(join.columns.stream().map(c -> c.name()).sorted().toList());
        relations.add(relation(r, source, target, sc, tc, RelationalRelationStorage.JOIN_TABLE, null, name, RelationalReferentialAction.NO_ACTION));
    }
    private void addForeignKey(UmlRelationship r, TableBuilder holder, TableBuilder referenced, boolean nullable, RelationalReferentialAction action, boolean unique, List<RelationalMappingDiagnostic> ds) {
        List<String> locals = new ArrayList<>();
        for (String key : referenced.primaryKey.columnNames()) { RelationalColumn pk = referenced.column(key); String name = referenced.name + "_" + key; holder.add(new RelationalColumn(RelationalColumnOrigin.FOREIGN_KEY, r.id(), name, name, pk.dataType(), nullable), ds, r.id()); locals.add(name); }
        locals.sort(String::compareTo); holder.foreignKeys.add(new RelationalForeignKey(r.id(), locals, referenced.name, referenced.primaryKey.columnNames(), action));
        if (unique) holder.uniques.add(new RelationalUniqueConstraint(locals)); else holder.addIndex(locals);
    }
    private RelationalRelation relation(UmlRelationship r, TableBuilder s, TableBuilder t, RelationalCardinality sc, RelationalCardinality tc, RelationalRelationStorage storage, String owner, String join, RelationalReferentialAction action) {
        if (r.type() == UmlRelationshipType.ASSOCIATION && s.name.compareTo(t.name) > 0) return new RelationalRelation(r.id(), r.type(), t.name, s.name, tc, sc, storage, owner, join, action);
        return new RelationalRelation(r.id(), r.type(), s.name, t.name, sc, tc, storage, owner, join, action);
    }
    private RelationalCardinality cardinality(Multiplicity m, UmlRelationship r, List<RelationalMappingDiagnostic> ds) { if (m == null) { diag(ds, RelationalMappingDiagnosticCode.UNSUPPORTED_MULTIPLICITY, r.id(), "relationships", "Multiplicity is required."); return null; } if (m.lower()==1 && Objects.equals(m.upper(),1)) return RelationalCardinality.EXACTLY_ONE; if (m.lower()==0 && Objects.equals(m.upper(),1)) return RelationalCardinality.ZERO_OR_ONE; if (m.lower()==0 && m.upper()==null) return RelationalCardinality.ZERO_OR_MANY; if (m.lower()==1 && m.upper()==null) return RelationalCardinality.ONE_OR_MANY; diag(ds, RelationalMappingDiagnosticCode.UNSUPPORTED_MULTIPLICITY, r.id(), "relationships", "Multiplicity is not supported by the relational IR."); return null; }
    private boolean many(RelationalCardinality c) { return c == RelationalCardinality.ZERO_OR_MANY || c == RelationalCardinality.ONE_OR_MANY; }
    private RelationalDataType type(UmlDataType t) { return switch(t) { case STRING -> RelationalDataType.VARCHAR; case INTEGER -> RelationalDataType.INTEGER; case LONG -> RelationalDataType.BIGINT; case DECIMAL -> RelationalDataType.DECIMAL; case BOOLEAN -> RelationalDataType.BOOLEAN; case DATE -> RelationalDataType.DATE; case DATETIME -> RelationalDataType.TIMESTAMP; case UUID -> RelationalDataType.UUID; case CUSTOM -> throw new IllegalArgumentException("CUSTOM must be preflighted"); }; }
    private void diag(List<RelationalMappingDiagnostic> ds, RelationalMappingDiagnosticCode code, UUID id, String path, String message) { ds.add(new RelationalMappingDiagnostic(code, id, path, message)); }
    private Comparator<RelationalTable> tableOrder() { return Comparator.comparing(RelationalTable::name).thenComparing(t -> t.origin().umlElementId().toString()); }
    private static final class TableBuilder {
        final RelationalTableOrigin origin; final String logicalName; final String name; final List<RelationalColumn> columns = new ArrayList<>(); final List<RelationalForeignKey> foreignKeys = new ArrayList<>(); final List<RelationalUniqueConstraint> uniques = new ArrayList<>(); final List<RelationalIndex> indexes = new ArrayList<>(); RelationalPrimaryKey primaryKey;
        TableBuilder(RelationalTableOrigin origin, String logicalName, String name) { this.origin=origin; this.logicalName=logicalName; this.name=name; }
        void add(RelationalColumn column, List<RelationalMappingDiagnostic> ds, UUID id) { if (columns.stream().anyMatch(c -> c.name().equals(column.name()))) { ds.add(new RelationalMappingDiagnostic(RelationalMappingDiagnosticCode.RELATIONAL_COLUMN_NAME_COLLISION, id, "columns", "Column collision: " + column.name())); return; } columns.add(column); }
        void addIndex(List<String> names) { if (indexes.stream().noneMatch(i -> i.columnNames().equals(names))) indexes.add(new RelationalIndex(names)); }
        RelationalColumn column(String name) { return columns.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(); }
        RelationalTable build() { return new RelationalTable(origin, logicalName, name, columns.stream().sorted(Comparator.comparing(RelationalColumn::name).thenComparing(c -> c.sourceElementId().toString())).toList(), primaryKey, foreignKeys.stream().sorted(Comparator.comparing(RelationalForeignKey::referencedTableName).thenComparing(f -> String.join(",", f.columnNames())).thenComparing(f -> f.sourceRelationshipId().toString())).toList(), uniques.stream().sorted(Comparator.comparing(u -> String.join(",",u.columnNames()))).toList(), indexes.stream().sorted(Comparator.comparing(i -> String.join(",",i.columnNames()))).toList()); }
    }
}
