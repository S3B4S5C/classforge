package com.classforge.generation.spring.planning;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.relational.model.*;
import com.classforge.generation.spring.model.*;
import com.classforge.generation.spring.validation.*;
import com.classforge.project.domain.document.UmlRelationshipType;
import java.util.*;
import org.junit.jupiter.api.Test;

class SpringGenerationPlannerTests {
    private final SpringGenerationPlanner planner = new SpringGenerationPlanner(new SpringJavaNamingStrategy(), new SpringGenerationModelValidator());
    private final SpringGenerationConfig config = new SpringGenerationConfig("biblioteca-api", "com.example.biblioteca");

    @Test void plansSimpleAndCompositeEntitiesWithoutGenerationStrategy() {
        RelationalTable cliente = entity("Cliente", "cliente", List.of(attr("id", "id", RelationalDataType.UUID, false), attr("nombre", "nombre", RelationalDataType.VARCHAR, true), attr("activo", "activo", RelationalDataType.BOOLEAN, false)), List.of("id"));
        RelationalTable detail = entity("DetallePedido", "detalle_pedido", List.of(attr("pedidoId", "pedido_id", RelationalDataType.UUID, false), attr("productoId", "producto_id", RelationalDataType.UUID, false), attr("cantidad", "cantidad", RelationalDataType.INTEGER, false)), List.of("pedido_id", "producto_id"));
        SpringGenerationModel model = plan(List.of(cliente, detail), List.of());
        SpringEntityModel client = entity(model, "Cliente"); assertEquals(SpringIdKind.SIMPLE, client.id().kind()); assertEquals("UUID", client.id().typeSimpleName()); assertTrue(client.scalarFields().stream().filter(f -> f.fieldName().equals("id")).findFirst().orElseThrow().identifier());
        SpringEntityModel ordered = entity(model, "DetallePedido"); assertEquals(SpringIdKind.COMPOSITE, ordered.id().kind()); assertEquals("DetallePedidoId", ordered.id().idClassName()); assertEquals(List.of("pedidoId", "productoId"), ordered.id().fields().stream().map(SpringIdFieldModel::fieldName).toList());
        assertEquals("DetallePedidoId", repo(model, "DetallePedido").idTypeSimpleName());
    }

    @Test void plansDirectRelationshipsAndSuppressesForeignKeyScalars() {
        UUID relation = id("usuario-prestamo");
        RelationalTable user = entity("Usuario", "usuario", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable loan = entity("Prestamo", "prestamo", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(relation, "usuario_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("usuario_id"), "usuario", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        RelationalRelation r = relation(relation, UmlRelationshipType.ASSOCIATION, "usuario", "prestamo", RelationalRelationStorage.FOREIGN_KEY, "prestamo", null);
        SpringEntityModel planned = entity(plan(List.of(user, loan), List.of(r)), "Prestamo");
        assertTrue(planned.scalarFields().stream().noneMatch(f -> f.columnName().equals("usuario_id")));
        SpringDirectRelationModel direct = planned.directRelations().getFirst(); assertEquals(SpringDirectRelationKind.MANY_TO_ONE, direct.kind()); assertEquals("usuario", direct.fieldName()); assertFalse(direct.optional()); assertEquals(List.of("usuario_id", "id"), List.of(direct.joinColumns().getFirst().localColumnName(), direct.joinColumns().getFirst().referencedColumnName()));
    }

    @Test void plansOptionalOneToOneCompositeAndCompositionMetadata() {
        UUID one = id("direccion-cliente"), composite = id("invoice-tenant"), composition = id("pedido-linea");
        RelationalTable client = entity("Cliente", "cliente", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable address = entity("Direccion", "direccion", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(one, "cliente_id", RelationalDataType.UUID, true)), List.of("id"), List.of(fk(one, List.of("cliente_id"), "cliente", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(new RelationalUniqueConstraint(List.of("cliente_id"))), List.of());
        RelationalTable tenant = entity("Tenant", "tenant", List.of(attr("countryCode", "country_code", RelationalDataType.VARCHAR, false), attr("tenantId", "tenant_id", RelationalDataType.UUID, false)), List.of("country_code", "tenant_id"));
        RelationalTable invoice = entity("Invoice", "invoice", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(composite, "tenant_country_code", RelationalDataType.VARCHAR, false), fkColumn(composite, "tenant_tenant_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(composite, List.of("tenant_country_code", "tenant_tenant_id"), "tenant", List.of("country_code", "tenant_id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        RelationalTable order = entity("Pedido", "pedido", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable line = entity("LineaPedido", "linea_pedido", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(composition, "pedido_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(composition, List.of("pedido_id"), "pedido", List.of("id"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        SpringGenerationModel model = plan(List.of(client, address, tenant, invoice, order, line), List.of(relation(one, UmlRelationshipType.ASSOCIATION, "cliente", "direccion", RelationalRelationStorage.FOREIGN_KEY, "direccion", null), relation(composite, UmlRelationshipType.ASSOCIATION, "tenant", "invoice", RelationalRelationStorage.FOREIGN_KEY, "invoice", null), relation(composition, UmlRelationshipType.COMPOSITION, "pedido", "linea_pedido", RelationalRelationStorage.FOREIGN_KEY, "linea_pedido", null)));
        assertEquals(SpringDirectRelationKind.ONE_TO_ONE, entity(model, "Direccion").directRelations().getFirst().kind()); assertTrue(entity(model, "Direccion").directRelations().getFirst().optional());
        assertEquals(List.of("tenant_country_code", "tenant_tenant_id"), entity(model, "Invoice").directRelations().getFirst().joinColumns().stream().map(SpringJoinColumnModel::localColumnName).toList());
        assertTrue(entity(model, "LineaPedido").directRelations().getFirst().onDeleteCascade());
    }

    @Test void rejectsMixedForeignKeyNullabilityAndFieldCollisions() {
        UUID relation = id("mixed"); RelationalTable target = entity("Usuario", "usuario", List.of(attr("a", "a", RelationalDataType.UUID, false), attr("b", "b", RelationalDataType.UUID, false)), List.of("a", "b"));
        RelationalTable source = entity("Source", "source", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(relation, "usuario_a", RelationalDataType.UUID, true), fkColumn(relation, "usuario_b", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("usuario_a", "usuario_b"), "usuario", List.of("a", "b"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        assertCode(() -> plan(List.of(target, source), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "usuario", "source", RelationalRelationStorage.FOREIGN_KEY, "source", null))), SpringGenerationDiagnosticCode.RELATION_NULLABILITY_INCONSISTENT);
        RelationalTable singleTarget = entity("Usuario", "usuario_simple", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable colliding = entity("Prestamo", "prestamo", List.of(attr("id", "id", RelationalDataType.UUID, false), attr("usuario", "usuario", RelationalDataType.VARCHAR, false), fkColumn(relation, "usuario_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("usuario_id"), "usuario_simple", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        assertCode(() -> plan(List.of(singleTarget, colliding), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "usuario_simple", "prestamo", RelationalRelationStorage.FOREIGN_KEY, "prestamo", null))), SpringGenerationDiagnosticCode.JAVA_FIELD_NAME_COLLISION);
    }

    @Test void plansManyToManyAndJoinedInheritance() {
        UUID many = id("autor-libro"), inherited = id("empleado-persona"), manager = id("gerente-empleado");
        RelationalTable author = entity("Autor", "autor", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id")); RelationalTable book = entity("Libro", "libro", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable join = join("autor_libro", many, List.of(fkColumn(many, "autor_id", RelationalDataType.UUID, false), fkColumn(many, "libro_id", RelationalDataType.UUID, false)), List.of("autor_id", "libro_id"), List.of(fk(many, List.of("autor_id"), "autor", List.of("id"), RelationalReferentialAction.NO_ACTION), fk(many, List.of("libro_id"), "libro", List.of("id"), RelationalReferentialAction.NO_ACTION)));
        RelationalTable person = entity("Persona", "persona", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable employee = entity("Empleado", "empleado", List.of(inheritedColumn("id", RelationalDataType.UUID), attr("salario", "salario", RelationalDataType.DECIMAL, false)), List.of("id"), List.of(fk(inherited, List.of("id"), "persona", List.of("id"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        RelationalTable boss = entity("Gerente", "gerente", List.of(inheritedColumn("id", RelationalDataType.UUID), attr("nivel", "nivel", RelationalDataType.INTEGER, false)), List.of("id"), List.of(fk(manager, List.of("id"), "empleado", List.of("id"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        SpringGenerationModel model = plan(List.of(join, employee, book, person, author, boss), List.of(relation(many, UmlRelationshipType.ASSOCIATION, "autor", "libro", RelationalRelationStorage.JOIN_TABLE, null, "autor_libro"), relation(inherited, UmlRelationshipType.GENERALIZATION, "empleado", "persona", RelationalRelationStorage.JOINED_INHERITANCE, "empleado", null), relation(manager, UmlRelationshipType.GENERALIZATION, "gerente", "empleado", RelationalRelationStorage.JOINED_INHERITANCE, "gerente", null)));
        assertEquals(5, model.entities().size()); SpringManyToManyRelationModel relationModel = entity(model, "Autor").manyToManyRelations().getFirst(); assertEquals("libroSet", relationModel.fieldName()); assertEquals("autor_libro", relationModel.joinTableName());
        assertEquals(SpringInheritanceKind.JOINED_ROOT, entity(model, "Persona").inheritance().kind()); assertEquals("Persona", entity(model, "Empleado").inheritance().superClassName()); assertEquals("Empleado", entity(model, "Gerente").inheritance().superClassName()); assertFalse(entity(model, "Gerente").id().declaredByEntity()); assertEquals("UUID", repo(model, "Gerente").idTypeSimpleName());
    }

    @Test void rejectsBrokenJoinTablesInheritanceCyclesAndTypeCollisionsAndIsDeterministic() {
        UUID rel = id("broken"); RelationalTable a = entity("A", "a", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id")); RelationalTable b = entity("B", "b", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        assertCode(() -> plan(List.of(a, b), List.of(relation(rel, UmlRelationshipType.ASSOCIATION, "a", "b", RelationalRelationStorage.JOIN_TABLE, null, "missing"))), SpringGenerationDiagnosticCode.JOIN_TABLE_NOT_FOUND);
        assertCode(() -> plan(List.of(a, b), List.of(relation(rel, UmlRelationshipType.GENERALIZATION, "a", "b", RelationalRelationStorage.JOINED_INHERITANCE, "a", null), relation(id("cycle"), UmlRelationshipType.GENERALIZATION, "b", "a", RelationalRelationStorage.JOINED_INHERITANCE, "b", null))), SpringGenerationDiagnosticCode.INHERITANCE_CYCLE);
        assertCode(() -> plan(List.of(entity("URLExterna", "url_externa", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id")), entity("UrlExterna", "url_externa2", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"))), List.of()), SpringGenerationDiagnosticCode.JAVA_TYPE_NAME_COLLISION);
        assertEquals(plan(List.of(a, b), List.of()), plan(List.of(b, a), List.of()));
    }

    @Test void preservesCompositeManyToManyPairingAndDoesNotCreateJoinEntity() {
        UUID relation = id("owner-target");
        RelationalTable owner = entity("Owner", "owner", List.of(attr("countryCode", "country_code", RelationalDataType.VARCHAR, false), attr("ownerId", "owner_id", RelationalDataType.UUID, false)), List.of("country_code", "owner_id"));
        RelationalTable target = entity("Target", "target", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable join = join("owner_target", relation, List.of(fkColumn(relation, "owner_country_code", RelationalDataType.VARCHAR, false), fkColumn(relation, "owner_owner_id", RelationalDataType.UUID, false), fkColumn(relation, "target_id", RelationalDataType.UUID, false)), List.of("owner_country_code", "owner_owner_id", "target_id"), List.of(fk(relation, List.of("owner_country_code", "owner_owner_id"), "owner", List.of("country_code", "owner_id"), RelationalReferentialAction.NO_ACTION), fk(relation, List.of("target_id"), "target", List.of("id"), RelationalReferentialAction.NO_ACTION)));
        SpringGenerationModel model = plan(List.of(owner, target, join), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "owner", "target", RelationalRelationStorage.JOIN_TABLE, null, "owner_target")));
        SpringManyToManyRelationModel planned = entity(model, "Owner").manyToManyRelations().getFirst();
        assertEquals(List.of("owner_country_code", "owner_owner_id"), planned.joinColumns().stream().map(SpringJoinColumnModel::localColumnName).toList());
        assertEquals(List.of("country_code", "owner_id"), planned.joinColumns().stream().map(SpringJoinColumnModel::referencedColumnName).toList());
        assertEquals(List.of("target_id"), planned.inverseJoinColumns().stream().map(SpringJoinColumnModel::localColumnName).toList());
        assertEquals(List.of("Owner", "Target"), model.entities().stream().map(SpringEntityModel::className).toList());
        assertEquals(2, model.repositories().size());
    }

    @Test void plansJoinedCompositeIdentityAndRelationshipToSubclass() {
        UUID inherited = id("factura-documento"), projectRelation = id("proyecto-factura");
        RelationalTable document = entity("PedidoDocumento", "pedido_documento", List.of(attr("serie", "serie", RelationalDataType.VARCHAR, false), attr("numero", "numero", RelationalDataType.BIGINT, false)), List.of("serie", "numero"));
        RelationalTable invoice = entity("Factura", "factura", List.of(inheritedColumn("serie", RelationalDataType.VARCHAR), inheritedColumn("numero", RelationalDataType.BIGINT)), List.of("serie", "numero"), List.of(fk(inherited, List.of("serie", "numero"), "pedido_documento", List.of("serie", "numero"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        RelationalTable project = entity("Proyecto", "proyecto", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(projectRelation, "factura_serie", RelationalDataType.VARCHAR, false), fkColumn(projectRelation, "factura_numero", RelationalDataType.BIGINT, false)), List.of("id"), List.of(fk(projectRelation, List.of("factura_serie", "factura_numero"), "factura", List.of("serie", "numero"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        SpringGenerationModel model = plan(List.of(project, invoice, document), List.of(relation(inherited, UmlRelationshipType.GENERALIZATION, "factura", "pedido_documento", RelationalRelationStorage.JOINED_INHERITANCE, "factura", null), relation(projectRelation, UmlRelationshipType.ASSOCIATION, "proyecto", "factura", RelationalRelationStorage.FOREIGN_KEY, "proyecto", null)));
        SpringEntityModel root = entity(model, "PedidoDocumento"), subclass = entity(model, "Factura");
        assertEquals("PedidoDocumentoId", root.id().idClassName()); assertTrue(root.id().declaredByEntity()); assertEquals("PedidoDocumentoId", subclass.id().typeSimpleName()); assertFalse(subclass.id().declaredByEntity()); assertNotEquals("FacturaId", subclass.id().idClassName());
        assertEquals(List.of("serie", "numero"), subclass.inheritance().primaryKeyJoinColumns().stream().map(SpringJoinColumnModel::localColumnName).toList());
        assertTrue(subclass.scalarFields().isEmpty()); assertEquals("Factura", entity(model, "Proyecto").directRelations().getFirst().targetEntityClassName());
    }

    @Test void rejectsInvalidJoinedPrimaryKeyJoins() {
        UUID relation = id("joined-invalid"); RelationalTable parent = entity("Parent", "parent", List.of(attr("id", "id", RelationalDataType.UUID, false), attr("legacyCode", "legacy_code", RelationalDataType.VARCHAR, false)), List.of("id"));
        RelationalTable childOther = entity("Child", "child", List.of(inheritedColumn("id", RelationalDataType.UUID), fkColumn(relation, "other_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("other_id"), "parent", List.of("id"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        assertCode(() -> plan(List.of(parent, childOther), List.of(relation(relation, UmlRelationshipType.GENERALIZATION, "child", "parent", RelationalRelationStorage.JOINED_INHERITANCE, "child", null))), SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID);
        RelationalTable childLegacy = entity("Child", "child", List.of(inheritedColumn("id", RelationalDataType.UUID)), List.of("id"), List.of(fk(relation, List.of("id"), "parent", List.of("legacy_code"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        assertCode(() -> plan(List.of(parent, childLegacy), List.of(relation(relation, UmlRelationshipType.GENERALIZATION, "child", "parent", RelationalRelationStorage.JOINED_INHERITANCE, "child", null))), SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID);
        RelationalTable compositeParent = entity("CompositeParent", "composite_parent", List.of(attr("a", "a", RelationalDataType.UUID, false), attr("b", "b", RelationalDataType.UUID, false)), List.of("a", "b"));
        RelationalTable compositeChild = entity("CompositeChild", "composite_child", List.of(inheritedColumn("a", RelationalDataType.UUID), inheritedColumn("b", RelationalDataType.UUID)), List.of("a", "b"), List.of(fk(relation, List.of("a"), "composite_parent", List.of("a"), RelationalReferentialAction.CASCADE)), List.of(), List.of());
        assertCode(() -> plan(List.of(compositeParent, compositeChild), List.of(relation(relation, UmlRelationshipType.GENERALIZATION, "composite_child", "composite_parent", RelationalRelationStorage.JOINED_INHERITANCE, "composite_child", null))), SpringGenerationDiagnosticCode.INHERITANCE_PRIMARY_KEY_JOIN_INVALID);
    }

    @Test void preservesUniqueIndexesAndRejectsMissingOrAmbiguousDirectForeignKeys() {
        RelationalTable product = entity("Producto", "producto", List.of(attr("id", "id", RelationalDataType.UUID, false), attr("codigo", "codigo", RelationalDataType.VARCHAR, false), attr("version", "version", RelationalDataType.INTEGER, false), attr("estado", "estado", RelationalDataType.VARCHAR, false), attr("fechaCreacion", "fecha_creacion", RelationalDataType.TIMESTAMP, false)), List.of("id"), List.of(), List.of(new RelationalUniqueConstraint(List.of("codigo", "version"))), List.of(new RelationalIndex(List.of("estado", "fecha_creacion"))));
        SpringEntityModel planned = entity(plan(List.of(product), List.of()), "Producto"); assertEquals(List.of("codigo", "version"), planned.uniqueConstraints().getFirst().columnNames()); assertEquals(List.of("estado", "fecha_creacion"), planned.indexes().getFirst().columnNames());
        UUID relation = id("missing-fk"); RelationalTable target = entity("Target", "target", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id")); RelationalTable owner = entity("Owner", "owner", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        assertCode(() -> plan(List.of(target, owner), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "target", "owner", RelationalRelationStorage.FOREIGN_KEY, "owner", null))), SpringGenerationDiagnosticCode.RELATION_FOREIGN_KEY_NOT_FOUND);
        RelationalTable ambiguous = entity("Owner", "owner", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(relation, "target_a", RelationalDataType.UUID, false), fkColumn(relation, "target_b", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("target_a"), "target", List.of("id"), RelationalReferentialAction.NO_ACTION), fk(relation, List.of("target_b"), "target", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        assertCode(() -> plan(List.of(target, ambiguous), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "target", "owner", RelationalRelationStorage.FOREIGN_KEY, "owner", null))), SpringGenerationDiagnosticCode.RELATION_FOREIGN_KEY_AMBIGUOUS);
    }

    @Test void preservesAggregationWithoutDeleteCascadeAndRemainingStructuralDiagnostics() {
        UUID relation = id("biblioteca-libro"); RelationalTable library = entity("Biblioteca", "biblioteca", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable book = entity("Libro", "libro", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(relation, "biblioteca_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(relation, List.of("biblioteca_id"), "biblioteca", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        assertFalse(entity(plan(List.of(library, book), List.of(relation(relation, UmlRelationshipType.AGGREGATION, "biblioteca", "libro", RelationalRelationStorage.FOREIGN_KEY, "libro", null))), "Libro").directRelations().getFirst().onDeleteCascade());
        RelationalTable noAttributePk = entity("Broken", "broken", List.of(fkColumn(relation, "id", RelationalDataType.UUID, false)), List.of("id"));
        assertCode(() -> plan(List.of(noAttributePk), List.of()), SpringGenerationDiagnosticCode.ENTITY_PRIMARY_KEY_INVALID);
        assertCode(() -> plan(List.of(library, book), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "biblioteca", "libro", RelationalRelationStorage.FOREIGN_KEY, "missing", null))), SpringGenerationDiagnosticCode.RELATION_OWNER_NOT_FOUND);
        RelationalTable invalidJoin = join("biblioteca_libro", relation, List.of(fkColumn(relation, "biblioteca_id", RelationalDataType.UUID, false)), List.of("biblioteca_id"), List.of(fk(relation, List.of("biblioteca_id"), "biblioteca", List.of("id"), RelationalReferentialAction.NO_ACTION)));
        assertCode(() -> plan(List.of(library, book, invalidJoin), List.of(relation(relation, UmlRelationshipType.ASSOCIATION, "biblioteca", "libro", RelationalRelationStorage.JOIN_TABLE, null, "biblioteca_libro"))), SpringGenerationDiagnosticCode.JOIN_TABLE_FOREIGN_KEYS_INVALID);
        assertCode(() -> plan(List.of(library), List.of(relation(relation, UmlRelationshipType.GENERALIZATION, "biblioteca", "missing", RelationalRelationStorage.JOINED_INHERITANCE, "biblioteca", null))), SpringGenerationDiagnosticCode.INHERITANCE_PARENT_NOT_FOUND);
    }

    @Test void rejectsIdClassCollisionAndIsDeterministicAcrossAllRelevantLists() {
        RelationalTable detail = entity("DetallePedido", "detalle_pedido", List.of(attr("pedido", "pedido", RelationalDataType.UUID, false), attr("producto", "producto", RelationalDataType.UUID, false)), List.of("pedido", "producto"));
        RelationalTable collision = entity("DetallePedidoId", "detalle_pedido_id", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        assertCode(() -> plan(List.of(detail, collision), List.of()), SpringGenerationDiagnosticCode.JAVA_TYPE_NAME_COLLISION);
        UUID direct = id("book-author"), many = id("book-category");
        RelationalTable author = entity("Autor", "autor", List.of(attr("id", "id", RelationalDataType.UUID, false), attr("codigo", "codigo", RelationalDataType.VARCHAR, false), attr("version", "version", RelationalDataType.INTEGER, false), attr("estado", "estado", RelationalDataType.VARCHAR, false), attr("fecha", "fecha", RelationalDataType.TIMESTAMP, false)), List.of("id"), List.of(), List.of(new RelationalUniqueConstraint(List.of("codigo", "version")), new RelationalUniqueConstraint(List.of("estado", "fecha"))), List.of(new RelationalIndex(List.of("estado", "fecha")), new RelationalIndex(List.of("codigo", "version"))));
        RelationalTable book = entity("Libro", "libro", List.of(attr("id", "id", RelationalDataType.UUID, false), fkColumn(direct, "autor_id", RelationalDataType.UUID, false)), List.of("id"), List.of(fk(direct, List.of("autor_id"), "autor", List.of("id"), RelationalReferentialAction.NO_ACTION)), List.of(), List.of());
        RelationalTable category = entity("Categoria", "categoria", List.of(attr("id", "id", RelationalDataType.UUID, false)), List.of("id"));
        RelationalTable join = join("libro_categoria", many, List.of(fkColumn(many, "libro_id", RelationalDataType.UUID, false), fkColumn(many, "categoria_id", RelationalDataType.UUID, false)), List.of("libro_id", "categoria_id"), List.of(fk(many, List.of("libro_id"), "libro", List.of("id"), RelationalReferentialAction.NO_ACTION), fk(many, List.of("categoria_id"), "categoria", List.of("id"), RelationalReferentialAction.NO_ACTION)));
        RelationalModel first = new RelationalModel("1.0", List.of(author, book, category, join), List.of(relation(direct, UmlRelationshipType.ASSOCIATION, "autor", "libro", RelationalRelationStorage.FOREIGN_KEY, "libro", null), relation(many, UmlRelationshipType.ASSOCIATION, "libro", "categoria", RelationalRelationStorage.JOIN_TABLE, null, "libro_categoria")));
        RelationalTable authorPermuted = new RelationalTable(author.origin(), author.logicalName(), author.name(), reverse(author.columns()), author.primaryKey(), reverse(author.foreignKeys()), reverse(author.uniqueConstraints()), reverse(author.indexes()));
        RelationalTable bookPermuted = new RelationalTable(book.origin(), book.logicalName(), book.name(), reverse(book.columns()), book.primaryKey(), reverse(book.foreignKeys()), reverse(book.uniqueConstraints()), reverse(book.indexes()));
        RelationalTable joinPermuted = new RelationalTable(join.origin(), join.logicalName(), join.name(), reverse(join.columns()), join.primaryKey(), reverse(join.foreignKeys()), reverse(join.uniqueConstraints()), reverse(join.indexes()));
        RelationalModel second = new RelationalModel("1.0", List.of(joinPermuted, category, bookPermuted, authorPermuted), List.of(first.relations().get(1), first.relations().get(0)));
        assertEquals(planner.plan(first, config), planner.plan(second, config));
    }

    private SpringGenerationModel plan(List<RelationalTable> tables, List<RelationalRelation> relations) { return planner.plan(new RelationalModel("1.0", tables, relations), config); }
    private SpringEntityModel entity(SpringGenerationModel model, String name) { return model.entities().stream().filter(e -> e.className().equals(name)).findFirst().orElseThrow(); }
    private SpringRepositoryModel repo(SpringGenerationModel model, String name) { return model.repositories().stream().filter(r -> r.entityClassName().equals(name)).findFirst().orElseThrow(); }
    private RelationalTable entity(String logical, String name, List<RelationalColumn> columns, List<String> pk) { return entity(logical, name, columns, pk, List.of(), List.of(), List.of()); }
    private RelationalTable entity(String logical, String name, List<RelationalColumn> columns, List<String> pk, List<RelationalForeignKey> fks, List<RelationalUniqueConstraint> unique, List<RelationalIndex> indexes) { return new RelationalTable(new RelationalTableOrigin(RelationalTableOriginType.UML_CLASS, id(name)), logical, name, columns, new RelationalPrimaryKey(pk), fks, unique, indexes); }
    private RelationalTable join(String name, UUID relationship, List<RelationalColumn> columns, List<String> pk, List<RelationalForeignKey> fks) { return new RelationalTable(new RelationalTableOrigin(RelationalTableOriginType.JOIN_RELATIONSHIP, relationship), name, name, columns, new RelationalPrimaryKey(pk), fks, List.of(), List.of()); }
    private RelationalColumn attr(String logical, String name, RelationalDataType type, boolean nullable) { return new RelationalColumn(RelationalColumnOrigin.ATTRIBUTE, UUID.randomUUID(), logical, name, type, nullable); }
    private RelationalColumn fkColumn(UUID relationship, String name, RelationalDataType type, boolean nullable) { return new RelationalColumn(RelationalColumnOrigin.FOREIGN_KEY, relationship, name, name, type, nullable); }
    private RelationalColumn inheritedColumn(String name, RelationalDataType type) { return new RelationalColumn(RelationalColumnOrigin.INHERITED_PRIMARY_KEY, UUID.randomUUID(), name, name, type, false); }
    private RelationalForeignKey fk(UUID relationship, List<String> local, String target, List<String> referenced, RelationalReferentialAction onDelete) { return new RelationalForeignKey(relationship, local, target, referenced, onDelete); }
    private RelationalRelation relation(UUID id, UmlRelationshipType type, String source, String target, RelationalRelationStorage storage, String owner, String join) { return new RelationalRelation(id, type, source, target, null, null, storage, owner, join, RelationalReferentialAction.NO_ACTION); }
    private UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private void assertCode(org.junit.jupiter.api.function.Executable action, SpringGenerationDiagnosticCode code) { assertTrue(assertThrows(SpringGenerationException.class, action).diagnostics().stream().anyMatch(d -> d.code() == code)); }
    private <T> List<T> reverse(List<T> values) { List<T> copy = new ArrayList<>(values); Collections.reverse(copy); return copy; }
}
