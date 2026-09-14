package com.classforge.integration.xmi;

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
