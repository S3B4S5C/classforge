package com.classforge.generation.relational;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.relational.model.*;
import com.classforge.project.domain.document.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RelationalModelMapperTests {
    private final RelationalModelMapper mapper = new RelationalModelMapper(new RelationalNamingStrategy(), new RelationalModelValidator());
    @Test void mapsSimpleClassAndCompositeKey() {
        UmlClass detail = clazz("DetallePedido", attr("pedidoId", UmlDataType.UUID, true), attr("productoId", UmlDataType.UUID, true), attr("cantidad", UmlDataType.INTEGER, false));
        RelationalTable table = mapper.map(new UmlModel(List.of(detail), List.of())).tables().getFirst();
        assertEquals("detalle_pedido", table.name()); assertEquals(List.of("pedido_id", "producto_id"), table.primaryKey().columnNames());
    }
    @Test void rejectsRootWithoutIdAndCustom() {
        assertCode(new UmlModel(List.of(clazz("Usuario", attr("nombre", UmlDataType.STRING, false))), List.of()), RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED);
        assertCode(new UmlModel(List.of(clazz("Usuario", attr("id", UmlDataType.UUID, true), attr("direccion", UmlDataType.CUSTOM, false))), List.of()), RelationalMappingDiagnosticCode.CUSTOM_TYPE_UNSUPPORTED);
    }
    @Test void mapsOneToManyAndOptionalForeignKey() {
        UmlClass user = identified("Usuario"), loan = identified("Prestamo");
        UmlRelationship relationship = relation(user, loan, UmlRelationshipType.ASSOCIATION, one(), many());
        RelationalTable loanTable = table(mapper.map(new UmlModel(List.of(user, loan), List.of(relationship))), "prestamo");
        assertFalse(loanTable.columns().stream().filter(c -> c.name().equals("usuario_id")).findFirst().orElseThrow().nullable());
        assertEquals(1, loanTable.indexes().size());
        UmlRelationship optional = new UmlRelationship(relationship.id(), user.id(), loan.id(), UmlRelationshipType.ASSOCIATION, new Multiplicity(0, 1), many());
        assertTrue(table(mapper.map(new UmlModel(List.of(user, loan), List.of(optional))), "prestamo").columns().stream().filter(c -> c.name().equals("usuario_id")).findFirst().orElseThrow().nullable());
    }
    @Test void mapsOneToOneIndependentlyOfAssociationDrawing() {
        UmlClass a = identified("A"), b = identified("B");
        UUID id = UUID.randomUUID();
        RelationalModel model = mapper.map(new UmlModel(List.of(a, b), List.of(new UmlRelationship(id, a.id(), b.id(), UmlRelationshipType.ASSOCIATION, one(), zeroOne()))));
        RelationalModel inverted = mapper.map(new UmlModel(List.of(b, a), List.of(new UmlRelationship(id, b.id(), a.id(), UmlRelationshipType.ASSOCIATION, zeroOne(), one()))));
        assertEquals(model, inverted);
        RelationalTable holder = table(model, "b"); assertEquals(1, holder.uniqueConstraints().size()); assertNotNull(holder.foreignKeys().getFirst());
    }
    @Test void mapsOneToOneSameLowerToLexicographicallyGreaterTable() {
        UmlClass client = identified("Cliente"), address = identified("Direccion");
        RelationalTable holder = table(mapper.map(new UmlModel(List.of(client, address), List.of(relation(client, address, UmlRelationshipType.ASSOCIATION, one(), one())))), "direccion");
        assertEquals("cliente", holder.foreignKeys().getFirst().referencedTableName()); assertEquals(1, holder.uniqueConstraints().size());
    }
    @Test void mapsManyToManyCompositionAndJoinedInheritance() {
        UmlClass author = identified("Autor"), book = identified("Libro");
        RelationalModel many = mapper.map(new UmlModel(List.of(author, book), List.of(relation(author, book, UmlRelationshipType.ASSOCIATION, many(), many()))));
        assertNotNull(table(many, "autor_libro"));
        UmlClass order = identified("Pedido"), line = identified("LineaPedido");
        RelationalModel composition = mapper.map(new UmlModel(List.of(order, line), List.of(relation(order, line, UmlRelationshipType.COMPOSITION, one(), new Multiplicity(1, null)))));
        assertEquals(RelationalReferentialAction.CASCADE, table(composition, "linea_pedido").foreignKeys().getFirst().onDelete());
        UmlClass person = clazz("Persona", attr("id", UmlDataType.UUID, true)), employee = clazz("Empleado", attr("salario", UmlDataType.DECIMAL, false));
        RelationalModel inherited = mapper.map(new UmlModel(List.of(person, employee), List.of(relation(employee, person, UmlRelationshipType.GENERALIZATION, null, null))));
        assertEquals(List.of("id"), table(inherited, "empleado").primaryKey().columnNames());
    }
    @Test void rejectsUnsupportedMappingsAndIsDeterministic() {
        UmlClass employee = identified("Empleado"); assertCode(new UmlModel(List.of(employee), List.of(relation(employee, employee, UmlRelationshipType.ASSOCIATION, one(), many()))), RelationalMappingDiagnosticCode.REFLEXIVE_RELATIONSHIP_UNSUPPORTED);
        UmlClass a = identified("A"), b = identified("B"); assertCode(new UmlModel(List.of(a, b), List.of(relation(a, b, UmlRelationshipType.ASSOCIATION, new Multiplicity(2, 5), one()))), RelationalMappingDiagnosticCode.UNSUPPORTED_MULTIPLICITY);
        RelationalModel first = mapper.map(new UmlModel(List.of(a, b), List.of(relation(a, b, UmlRelationshipType.ASSOCIATION, one(), many()))));
        RelationalModel second = mapper.map(new UmlModel(List.of(b, a), List.of(firstRelation(first))));
        assertEquals(first, second);
    }
    @Test void doesNotMutateSourceCollectionsOrRelationship() {
        UmlAttribute userId = attr("id", UmlDataType.UUID, true), userName = attr("nombre", UmlDataType.STRING, false), loanId = attr("id", UmlDataType.UUID, true), loanDate = attr("fecha", UmlDataType.DATE, false);
        UmlClass user = clazz("Usuario", userId, userName), loan = clazz("Prestamo", loanId, loanDate);
        UmlRelationship relationship = relation(user, loan, UmlRelationshipType.ASSOCIATION, one(), many());
        List<UmlClass> originalClasses = List.of(user, loan); List<UmlRelationship> originalRelationships = List.of(relationship); Multiplicity sourceMultiplicity = relationship.sourceMultiplicity(); Multiplicity targetMultiplicity = relationship.targetMultiplicity();
        UmlModel source = new UmlModel(originalClasses, originalRelationships);
        mapper.map(source);
        assertEquals(originalClasses, source.classes()); assertEquals(originalRelationships, source.relationships());
        assertEquals(sourceMultiplicity, relationship.sourceMultiplicity()); assertEquals(targetMultiplicity, relationship.targetMultiplicity());
    }
    @Test void forcesDirectNullableIdentifierToNonNullablePrimaryKey() {
        UmlAttribute id = new UmlAttribute(UUID.randomUUID(), "id", UmlDataType.UUID, null, UmlVisibility.PRIVATE, true, true);
        RelationalTable table = mapper.map(new UmlModel(List.of(clazz("Usuario", id)), List.of())).tables().getFirst();
        assertFalse(table.columns().getFirst().nullable());
    }
    @Test void rejectsInheritanceAndNameCollisionBlockers() {
        UmlClass person = identified("Persona"), employee = clazz("Empleado", attr("employeeId", UmlDataType.UUID, true));
        assertCode(new UmlModel(List.of(person, employee), List.of(relation(employee, person, UmlRelationshipType.GENERALIZATION, null, null))), RelationalMappingDiagnosticCode.SUBCLASS_IDENTIFIER_NOT_ALLOWED);
        UmlClass a = identified("A"), b = identified("B"), c = identified("C");
        assertCode(new UmlModel(List.of(a, b, c), List.of(relation(c, a, UmlRelationshipType.GENERALIZATION, null, null), relation(c, b, UmlRelationshipType.GENERALIZATION, null, null))), RelationalMappingDiagnosticCode.MULTIPLE_INHERITANCE_UNSUPPORTED);
        UmlClass author = identified("Autor"), book = identified("Libro"), authorBook = identified("AutorLibro");
        assertCode(new UmlModel(List.of(author, book, authorBook), List.of(relation(author, book, UmlRelationshipType.ASSOCIATION, many(), many()))), RelationalMappingDiagnosticCode.RELATIONAL_TABLE_NAME_COLLISION);
    }
    @Test void mapsAggregationAndRejectsInvalidCompositionOwner() {
        UmlClass library = identified("Biblioteca"), book = identified("Libro");
        RelationalModel aggregation = mapper.map(new UmlModel(List.of(library, book), List.of(relation(library, book, UmlRelationshipType.AGGREGATION, one(), many()))));
        assertEquals(RelationalReferentialAction.NO_ACTION, table(aggregation, "libro").foreignKeys().getFirst().onDelete());
        assertCode(new UmlModel(List.of(library, book), List.of(relation(library, book, UmlRelationshipType.COMPOSITION, many(), one()))), RelationalMappingDiagnosticCode.COMPOSITION_OWNER_MULTIPLICITY_INVALID);
    }
    @Test void rejectsUnknownRelationshipReferencesWithoutPartialModel() {
        UmlClass b = identified("B"); UUID relationshipId = UUID.randomUUID();
        assertCode(new UmlModel(List.of(b), List.of(new UmlRelationship(relationshipId, UUID.randomUUID(), b.id(), UmlRelationshipType.ASSOCIATION, one(), many()))), RelationalMappingDiagnosticCode.RELATIONSHIP_CLASS_NOT_FOUND);
        assertCode(new UmlModel(List.of(b), List.of(new UmlRelationship(relationshipId, b.id(), UUID.randomUUID(), UmlRelationshipType.ASSOCIATION, one(), many()))), RelationalMappingDiagnosticCode.RELATIONSHIP_CLASS_NOT_FOUND);
        RelationalMappingException exception = assertThrows(RelationalMappingException.class, () -> mapper.map(new UmlModel(List.of(b), List.of(new UmlRelationship(relationshipId, UUID.randomUUID(), UUID.randomUUID(), UmlRelationshipType.ASSOCIATION, one(), many())))));
        assertEquals(2, exception.diagnostics().stream().filter(d -> d.code() == RelationalMappingDiagnosticCode.RELATIONSHIP_CLASS_NOT_FOUND).count());
    }
    @Test void mapsCompositeForeignKeyAndDetailedManyToManyIndependentOfDrawing() {
        UmlClass tenant = clazz("Tenant", attr("countryCode", UmlDataType.STRING, true), attr("tenantId", UmlDataType.UUID, true)); UmlClass invoice = identified("Invoice");
        RelationalTable invoiceTable = table(mapper.map(new UmlModel(List.of(tenant, invoice), List.of(relation(tenant, invoice, UmlRelationshipType.ASSOCIATION, one(), many())))), "invoice");
        assertEquals(List.of("tenant_country_code", "tenant_tenant_id"), invoiceTable.foreignKeys().getFirst().columnNames()); assertEquals(List.of("country_code", "tenant_id"), invoiceTable.foreignKeys().getFirst().referencedColumnNames()); assertEquals(1, invoiceTable.indexes().size());
        UmlClass author = identified("Autor"), book = identified("Libro"); UUID id = UUID.randomUUID();
        RelationalModel first = mapper.map(new UmlModel(List.of(author, book), List.of(new UmlRelationship(id, author.id(), book.id(), UmlRelationshipType.ASSOCIATION, many(), many()))));
        RelationalModel inverted = mapper.map(new UmlModel(List.of(book, author), List.of(new UmlRelationship(id, book.id(), author.id(), UmlRelationshipType.ASSOCIATION, many(), many()))));
        RelationalTable join = table(first, "autor_libro"); assertEquals(first, inverted); assertEquals(List.of("autor_id", "libro_id"), join.primaryKey().columnNames()); assertEquals(2, join.foreignKeys().size()); assertTrue(join.columns().stream().noneMatch(RelationalColumn::nullable)); assertEquals("autor_libro", first.relations().getFirst().joinTableName());
    }
    @Test void rejectsForeignKeyColumnCollisionAndUnsupportedMultiplicity() {
        UmlClass user = identified("Usuario"), loan = clazz("Prestamo", attr("id", UmlDataType.UUID, true), attr("usuarioId", UmlDataType.UUID, false));
        assertCode(new UmlModel(List.of(user, loan), List.of(relation(user, loan, UmlRelationshipType.ASSOCIATION, one(), many()))), RelationalMappingDiagnosticCode.RELATIONAL_COLUMN_NAME_COLLISION);
        assertCode(new UmlModel(List.of(user, loan), List.of(relation(user, loan, UmlRelationshipType.ASSOCIATION, new Multiplicity(2, null), one()))), RelationalMappingDiagnosticCode.UNSUPPORTED_MULTIPLICITY);
    }
    @Test void mapsMultilevelInheritanceAndRelationshipToSubclassIndependentlyOfRelationshipOrder() {
        UmlClass person = identified("Persona"), employee = clazz("Empleado", attr("salario", UmlDataType.DECIMAL, false)), manager = clazz("Gerente", attr("nivel", UmlDataType.INTEGER, false)), project = identified("Proyecto");
        UUID employeeParent = UUID.randomUUID(), managerParent = UUID.randomUUID(), projectEmployee = UUID.randomUUID();
        UmlRelationship ep = new UmlRelationship(employeeParent, employee.id(), person.id(), UmlRelationshipType.GENERALIZATION, null, null), mp = new UmlRelationship(managerParent, manager.id(), employee.id(), UmlRelationshipType.GENERALIZATION, null, null), pe = new UmlRelationship(projectEmployee, project.id(), employee.id(), UmlRelationshipType.ASSOCIATION, many(), one());
        RelationalModel first = mapper.map(new UmlModel(List.of(person, employee, manager, project), List.of(ep, mp, pe)));
        RelationalModel second = mapper.map(new UmlModel(List.of(project, manager, employee, person), List.of(pe, mp, ep)));
        assertEquals(first, second); assertEquals("empleado", table(first, "proyecto").foreignKeys().getFirst().referencedTableName()); assertEquals("empleado", table(first, "gerente").foreignKeys().getFirst().referencedTableName());
        assertEquals(RelationalColumnOrigin.INHERITED_PRIMARY_KEY, table(first, "empleado").columns().stream().filter(column -> column.name().equals("id")).findFirst().orElseThrow().origin());
    }
    @Test void rejectsGeneralizationCycle() {
        UmlClass a = identified("A"), b = identified("B");
        assertCode(new UmlModel(List.of(a, b), List.of(relation(a, b, UmlRelationshipType.GENERALIZATION, null, null), relation(b, a, UmlRelationshipType.GENERALIZATION, null, null))), RelationalMappingDiagnosticCode.GENERALIZATION_CYCLE);
    }
    @Test void isDeterministicAcrossClassAttributeAndRelationshipPermutations() {
        UUID aId = UUID.randomUUID(), bId = UUID.randomUUID(), cId = UUID.randomUUID();
        UmlAttribute aIdAttribute = attr("id", UmlDataType.UUID, true), aName = attr("nombre", UmlDataType.STRING, false), bIdAttribute = attr("id", UmlDataType.UUID, true), bName = attr("fechaRegistro", UmlDataType.DATETIME, false), cIdAttribute = attr("id", UmlDataType.UUID, true), cName = attr("activo", UmlDataType.BOOLEAN, false);
        UmlClass a = new UmlClass(aId, "Autor", List.of(aIdAttribute, aName)), b = new UmlClass(bId, "Libro", List.of(bIdAttribute, bName)), c = new UmlClass(cId, "Editorial", List.of(cIdAttribute, cName));
        UUID ab = UUID.randomUUID(), bc = UUID.randomUUID(); UmlRelationship authorBooks = new UmlRelationship(ab, aId, bId, UmlRelationshipType.ASSOCIATION, one(), many()), bookPublisher = new UmlRelationship(bc, bId, cId, UmlRelationshipType.ASSOCIATION, many(), many());
        RelationalModel first = mapper.map(new UmlModel(List.of(a, b, c), List.of(authorBooks, bookPublisher)));
        RelationalModel second = mapper.map(new UmlModel(List.of(new UmlClass(cId, "Editorial", List.of(cName, cIdAttribute)), new UmlClass(bId, "Libro", List.of(bName, bIdAttribute)), new UmlClass(aId, "Autor", List.of(aName, aIdAttribute))), List.of(bookPublisher, authorBooks)));
        assertEquals(first, second);
    }
    @Test void mapsAssociationClassMaterializationAsEntityWithBothForeignKeys() {
        UmlClass order = identified("Pedido"), product = identified("Producto");
        UmlRelationship original = new UmlRelationship(
                UUID.randomUUID(), order.id(), product.id(), UmlRelationshipType.COMPOSITION,
                new Multiplicity(1, null), new Multiplicity(1, null)
        );
        UmlClass detail = AssociationClassSupport.withMarker(
                clazz("DetallePedido", attr("id", UmlDataType.UUID, true), attr("cantidad", UmlDataType.INTEGER, false)),
                original
        );
        UmlRelationship orderDetail = new UmlRelationship(
                UUID.randomUUID(), order.id(), detail.id(), UmlRelationshipType.ASSOCIATION,
                one(), new Multiplicity(1, null)
        );
        UmlRelationship productDetail = new UmlRelationship(
                UUID.randomUUID(), product.id(), detail.id(), UmlRelationshipType.ASSOCIATION,
                one(), new Multiplicity(1, null)
        );

        RelationalTable detailTable = table(
                mapper.map(new UmlModel(List.of(order, product, detail), List.of(orderDetail, productDetail))),
                "detalle_pedido"
        );

        assertEquals(Set.of("pedido", "producto"), detailTable.foreignKeys().stream()
                .map(RelationalForeignKey::referencedTableName)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(detailTable.columns().stream().anyMatch(column -> column.name().equals("pedido_id")));
        assertTrue(detailTable.columns().stream().anyMatch(column -> column.name().equals("producto_id")));
        assertTrue(detailTable.columns().stream().anyMatch(column -> column.name().equals("cantidad")));
    }


    @Test void associationClassOwnsBothForeignKeysEvenWhenOriginalEndsAreOneToOne() {
        UmlClass order = identified("PedidoUno"), product = identified("ProductoUno");
        UmlRelationship original = new UmlRelationship(
                UUID.randomUUID(), order.id(), product.id(), UmlRelationshipType.ASSOCIATION,
                one(), one()
        );
        UmlClass detail = AssociationClassSupport.withMarker(
                clazz("DetalleUno", attr("id", UmlDataType.UUID, true), attr("nota", UmlDataType.STRING, false)),
                original
        );
        UmlRelationship orderDetail = new UmlRelationship(
                UUID.randomUUID(), order.id(), detail.id(), UmlRelationshipType.ASSOCIATION,
                one(), one()
        );
        UmlRelationship productDetail = new UmlRelationship(
                UUID.randomUUID(), product.id(), detail.id(), UmlRelationshipType.ASSOCIATION,
                one(), one()
        );

        RelationalModel model = mapper.map(new UmlModel(
                List.of(order, product, detail), List.of(orderDetail, productDetail)
        ));
        RelationalTable detailTable = table(model, "detalle_uno");
        RelationalTable orderTable = table(model, "pedido_uno");
        RelationalTable productTable = table(model, "producto_uno");

        assertEquals(Set.of("pedido_uno", "producto_uno"), detailTable.foreignKeys().stream()
                .map(RelationalForeignKey::referencedTableName)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(orderTable.foreignKeys().isEmpty());
        assertTrue(productTable.foreignKeys().isEmpty());
        assertEquals(2, detailTable.uniqueConstraints().size());
    }

    @Test void rejectsPhysicalNamingCollision() {
        assertCode(new UmlModel(List.of(identified("URLValue"), identified("UrlValue")), List.of()), RelationalMappingDiagnosticCode.RELATIONAL_TABLE_NAME_COLLISION);
    }
    private UmlRelationship firstRelation(RelationalModel model) { RelationalRelation r = model.relations().getFirst(); return new UmlRelationship(r.sourceRelationshipId(), tableClassId(model, r.sourceTableName()), tableClassId(model, r.targetTableName()), r.umlType(), one(), many()); }
    private UUID tableClassId(RelationalModel model, String name) { return table(model, name).origin().umlElementId(); }
    private void assertCode(UmlModel model, RelationalMappingDiagnosticCode code) { assertTrue(assertThrows(RelationalMappingException.class, () -> mapper.map(model)).diagnostics().stream().anyMatch(d -> d.code() == code)); }
    private RelationalTable table(RelationalModel model, String name) { return model.tables().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow(); }
    private UmlClass identified(String name) { return clazz(name, attr("id", UmlDataType.UUID, true)); }
    private UmlClass clazz(String name, UmlAttribute... attributes) { return new UmlClass(UUID.randomUUID(), name, List.of(attributes)); }
    private UmlAttribute attr(String name, UmlDataType type, boolean id) { return new UmlAttribute(UUID.randomUUID(), name, type, type == UmlDataType.CUSTOM ? "Direccion" : null, UmlVisibility.PRIVATE, false, id); }
    private UmlRelationship relation(UmlClass source, UmlClass target, UmlRelationshipType type, Multiplicity sm, Multiplicity tm) { return new UmlRelationship(UUID.randomUUID(), source.id(), target.id(), type, sm, tm); }
    private Multiplicity one() { return new Multiplicity(1, 1); } private Multiplicity zeroOne() { return new Multiplicity(0, 1); } private Multiplicity many() { return new Multiplicity(0, null); }
}
