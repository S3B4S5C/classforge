package com.classforge.generation.relational;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.relational.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RelationalModelValidatorTests {
    private final RelationalModelValidator validator = new RelationalModelValidator();

    @Test void acceptsValidModelAndRejectsPrimaryKeyAndDuplicateInvariants() {
        RelationalTable user = table("usuario", columns(column("id", false)), key("id"), List.of(), List.of(), List.of());
        assertDoesNotThrow(() -> validator.validate(model(List.of(user), List.of())));
        invalid(table("bad", columns(column("id", false)), key("missing"), List.of(), List.of(), List.of()));
        invalid(table("bad", columns(column("id", true)), key("id"), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(model(List.of(user, user), List.of())));
        invalid(table("bad", columns(column("id", false), column("id", false)), key("id"), List.of(), List.of(), List.of()));
    }

    @Test void rejectsForeignKeyAndConstraintInvariants() {
        RelationalTable target = table("target", columns(column("id", false)), key("id"), List.of(), List.of(), List.of());
        invalid(table("child", columns(column("id", false)), key("id"), List.of(fk("missing", "target", "id")), List.of(), List.of()), target);
        invalid(table("child", columns(column("id", false), column("target_id", false)), key("id"), List.of(fk("target_id", "missing", "id")), List.of(), List.of()));
        invalid(table("child", columns(column("id", false), column("target_id", false)), key("id"), List.of(fk("target_id", "target", "missing")), List.of(), List.of()), target);
        RelationalForeignKey mismatch = new RelationalForeignKey(UUID.randomUUID(), List.of("target_id", "id"), "target", List.of("id"), RelationalReferentialAction.NO_ACTION);
        invalid(table("child", columns(column("id", false), column("target_id", false)), key("id"), List.of(mismatch), List.of(), List.of()), target);
        invalid(table("child", columns(column("id", false), column("target_id", false)), key("id"), List.of(fk("target_id", "target", "id")), List.of(new RelationalUniqueConstraint(List.of("missing"))), List.of()), target);
        invalid(table("child", columns(column("id", false), column("target_id", false)), key("id"), List.of(fk("target_id", "target", "id")), List.of(), List.of(new RelationalIndex(List.of("missing")))), target);
    }

    @Test void rejectsRelationInvariants() {
        RelationalTable user = table("usuario", columns(column("id", false)), key("id"), List.of(), List.of(), List.of());
        UUID id = UUID.randomUUID();
        RelationalRelation badJoin = new RelationalRelation(id, null, "usuario", "usuario", null, null, RelationalRelationStorage.JOIN_TABLE, null, "missing", RelationalReferentialAction.NO_ACTION);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(model(List.of(user), List.of(badJoin))));
        RelationalRelation badOwner = new RelationalRelation(id, null, "usuario", "usuario", null, null, RelationalRelationStorage.FOREIGN_KEY, "missing", null, RelationalReferentialAction.NO_ACTION);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(model(List.of(user), List.of(badOwner))));
        RelationalRelation valid = new RelationalRelation(id, null, "usuario", "usuario", null, null, RelationalRelationStorage.FOREIGN_KEY, "usuario", null, RelationalReferentialAction.NO_ACTION);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(model(List.of(user), List.of(valid, valid))));
    }

    private void invalid(RelationalTable table, RelationalTable... others) { List<RelationalTable> tables = new ArrayList<>(List.of(others)); tables.add(table); assertThrows(IllegalArgumentException.class, () -> validator.validate(model(tables, List.of()))); }
    private RelationalModel model(List<RelationalTable> tables, List<RelationalRelation> relations) { return new RelationalModel("1.0", tables, relations); }
    private RelationalTable table(String name, List<RelationalColumn> columns, RelationalPrimaryKey key, List<RelationalForeignKey> fks, List<RelationalUniqueConstraint> uniques, List<RelationalIndex> indexes) { return new RelationalTable(new RelationalTableOrigin(RelationalTableOriginType.UML_CLASS, UUID.randomUUID()), name, name, columns, key, fks, uniques, indexes); }
    private List<RelationalColumn> columns(RelationalColumn... values) { return List.of(values); }
    private RelationalColumn column(String name, boolean nullable) { return new RelationalColumn(RelationalColumnOrigin.ATTRIBUTE, UUID.randomUUID(), name, name, RelationalDataType.UUID, nullable); }
    private RelationalPrimaryKey key(String name) { return new RelationalPrimaryKey(List.of(name)); }
    private RelationalForeignKey fk(String local, String target, String referenced) { return new RelationalForeignKey(UUID.randomUUID(), List.of(local), target, List.of(referenced), RelationalReferentialAction.NO_ACTION); }
}
