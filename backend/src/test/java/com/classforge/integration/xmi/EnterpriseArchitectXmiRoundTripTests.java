package com.classforge.integration.xmi;

import com.classforge.project.domain.document.AssociationClassSupport;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseArchitectXmiRoundTripTests {

    private final EnterpriseArchitectXmiExporter exporter = new EnterpriseArchitectXmiExporter();
    private final EnterpriseArchitectXmiImporter importer = new EnterpriseArchitectXmiImporter();
    private final ProjectDocumentValidator validator = new ProjectDocumentValidator();

    @Test
    void exportIsDeterministicAndSemanticRoundTripPreservesCanonicalIds() {
        ProjectDocument original = fixture();
        UUID projectId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        byte[] first = exporter.export(projectId, "Veterinaria Central", original);
        byte[] second = exporter.export(projectId, "Veterinaria Central", original);
        assertArrayEquals(first, second);

        String xml = new String(first, StandardCharsets.UTF_8);
        assertTrue(xml.contains("xmi:version=\"2.1\""));
        assertTrue(xml.contains("exporter=\"ClassForge\""));
        assertTrue(xml.contains("aggregation=\"composite\""));
        assertTrue(xml.contains("aggregation=\"shared\""));
        assertTrue(xml.contains("<generalization"));

        XmiImportResult imported = importer.importXmi(first);
        validator.validate(imported.document());

        assertEquals(original.umlModel().classes(), imported.document().umlModel().classes());
        assertEquals(
                original.umlModel().relationships().stream().sorted(java.util.Comparator.comparing(item -> item.id().toString())).toList(),
                imported.document().umlModel().relationships().stream().sorted(java.util.Comparator.comparing(item -> item.id().toString())).toList()
        );
    }


    @Test
    void associationClassExportsAsNativeUmlAssociationClassAndRoundTripsSemantics() {
        UUID pedidoId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID productoId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID detalleId = UUID.fromString("10000000-0000-0000-0000-000000000003");
        UmlClass pedido = new UmlClass(pedidoId, "Pedido", List.of(
                new UmlAttribute(UUID.randomUUID(), "idPedido", UmlDataType.INTEGER, null, UmlVisibility.PUBLIC, false, true)
        ));
        UmlClass producto = new UmlClass(productoId, "Producto", List.of(
                new UmlAttribute(UUID.randomUUID(), "idProducto", UmlDataType.INTEGER, null, UmlVisibility.PUBLIC, false, true)
        ));
        UmlRelationship original = new UmlRelationship(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                pedidoId, productoId, UmlRelationshipType.COMPOSITION,
                new Multiplicity(1, null), new Multiplicity(1, null)
        );
        UmlClass detalle = AssociationClassSupport.withMarker(
                new UmlClass(detalleId, "DetallePedido", List.of(
                        new UmlAttribute(UUID.randomUUID(), "id", UmlDataType.UUID, null, UmlVisibility.PRIVATE, false, true),
                        new UmlAttribute(UUID.randomUUID(), "cantidad", UmlDataType.INTEGER, null, UmlVisibility.PUBLIC, false, false),
                        new UmlAttribute(UUID.randomUUID(), "precioUnitario", UmlDataType.DECIMAL, null, UmlVisibility.PUBLIC, false, false)
                )),
                original
        );
        UmlRelationship pedidoBridge = new UmlRelationship(
                UUID.randomUUID(), pedidoId, detalleId, UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(), original.targetMultiplicity()
        );
        UmlRelationship productoBridge = new UmlRelationship(
                UUID.randomUUID(), productoId, detalleId, UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(), original.sourceMultiplicity()
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(pedido, producto, detalle), List.of(pedidoBridge, productoBridge)),
                DiagramLayout.empty()
        );

        byte[] exported = exporter.export(UUID.randomUUID(), "Pedidos", document);
        String xml = new String(exported, StandardCharsets.UTF_8);

        assertTrue(xml.contains("xmi:type=\"uml:AssociationClass\""));
        assertTrue(xml.contains("name=\"DetallePedido\""));
        assertTrue(xml.contains("aggregation=\"composite\""));
        assertTrue(xml.contains("name=\"cantidad\""));
        assertTrue(xml.contains("name=\"precioUnitario\""));
        assertEquals(0, count(xml, "xmi:type=\"uml:Association\""));
        assertTrue(!xml.contains(AssociationClassSupport.MARKER_V1));
        assertTrue(!xml.contains(AssociationClassSupport.MARKER_V2));

        ProjectDocument imported = importer.importXmi(exported).document();
        validator.validate(imported);
        UmlClass importedDetalle = imported.umlModel().classes().stream()
                .filter(item -> item.name().equals("DetallePedido"))
                .findFirst().orElseThrow();
        UmlRelationship restored = AssociationClassSupport.metadata(importedDetalle)
                .orElseThrow().relationship();

        assertEquals(UmlRelationshipType.COMPOSITION, restored.type());
        assertEquals(new Multiplicity(1, null), restored.sourceMultiplicity());
        assertEquals(new Multiplicity(1, null), restored.targetMultiplicity());
        assertEquals(List.of("cantidad", "precioUnitario"), AssociationClassSupport.visibleAttributes(importedDetalle)
                .stream().map(UmlAttribute::name).filter(name -> !name.equals("id")).sorted().toList());
        assertEquals(2, AssociationClassSupport.auxiliaryRelationshipIds(imported).size());

        String reexported = new String(
                exporter.export(UUID.randomUUID(), "Pedidos", imported),
                StandardCharsets.UTF_8
        );
        assertTrue(reexported.contains("xmi:type=\"uml:AssociationClass\""));
        assertEquals(0, count(reexported, "xmi:type=\"uml:Association\""));
    }

    private long count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1L;
    }

    private ProjectDocument fixture() {
        UUID personId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID employeeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID addressId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID idAttribute = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID nameAttribute = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID codeAttribute = UUID.fromString("33333333-3333-3333-3333-333333333333");

        UmlClass person = new UmlClass(personId, "Persona", List.of(
                new UmlAttribute(idAttribute, "id", UmlDataType.UUID, null, UmlVisibility.PRIVATE, false, true),
                new UmlAttribute(nameAttribute, "nombre", UmlDataType.STRING, null, UmlVisibility.PRIVATE, false, false)
        ));
        UmlClass employee = new UmlClass(employeeId, "Empleado", List.of(
                new UmlAttribute(codeAttribute, "codigo", UmlDataType.CUSTOM, "CodigoEmpleado", UmlVisibility.PROTECTED, true, false)
        ));
        UmlClass address = new UmlClass(addressId, "Direccion", List.of());

        UmlRelationship inheritance = new UmlRelationship(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                employeeId,
                personId,
                UmlRelationshipType.GENERALIZATION,
                null,
                null
        );
        UmlRelationship composition = new UmlRelationship(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                personId,
                addressId,
                UmlRelationshipType.COMPOSITION,
                Multiplicity.one(),
                new Multiplicity(0, 1)
        );
        UmlRelationship aggregation = new UmlRelationship(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                employeeId,
                addressId,
                UmlRelationshipType.AGGREGATION,
                Multiplicity.one(),
                Multiplicity.many()
        );

        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(person, employee, address), List.of(inheritance, composition, aggregation)),
                new DiagramLayout(Map.of(
                        personId, DiagramNodeLayout.defaultForIndex(0),
                        employeeId, DiagramNodeLayout.defaultForIndex(1),
                        addressId, DiagramNodeLayout.defaultForIndex(2)
                ))
        );
    }
}
