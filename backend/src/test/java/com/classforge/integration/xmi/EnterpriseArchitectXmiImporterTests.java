package com.classforge.integration.xmi;

import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseArchitectXmiImporterTests {

    private final EnterpriseArchitectXmiImporter importer = new EnterpriseArchitectXmiImporter();
    private final ProjectDocumentValidator validator = new ProjectDocumentValidator();

    @Test
    void importsEnterpriseArchitectXmi21SubsetIntoCanonicalDocument() throws Exception {
        XmiImportResult result = importer.importXmi(fixture());

        assertEquals(1, result.packageCount());
        assertEquals(5, result.classCount());
        assertEquals(4, result.attributeCount());
        assertEquals(4, result.relationshipCount());
        assertEquals(5, result.document().layout().nodes().size());
        validator.validate(result.document());

        UmlClass persona = result.document().umlModel().classes().stream()
                .filter(item -> item.name().equals("Persona"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, persona.attributes().size());
        assertTrue(persona.attributes().getFirst().identifier());
        assertEquals(UmlDataType.UUID, persona.attributes().getFirst().dataType());
        assertEquals(UmlDataType.STRING, persona.attributes().get(1).dataType());
        assertFalse(persona.attributes().get(1).nullable());

        UmlClass veterinario = result.document().umlModel().classes().stream()
                .filter(item -> item.name().equals("Veterinario"))
                .findFirst()
                .orElseThrow();
        assertTrue(veterinario.attributes().getFirst().nullable());

        assertEquals(1, count(result, UmlRelationshipType.ASSOCIATION));
        assertEquals(1, count(result, UmlRelationshipType.AGGREGATION));
        assertEquals(1, count(result, UmlRelationshipType.COMPOSITION));
        assertEquals(1, count(result, UmlRelationshipType.GENERALIZATION));

        var composition = result.document().umlModel().relationships().stream()
                .filter(item -> item.type() == UmlRelationshipType.COMPOSITION)
                .findFirst()
                .orElseThrow();
        assertEquals(1, composition.sourceMultiplicity().lower());
        assertEquals(1, composition.sourceMultiplicity().upper());
        assertEquals(1, composition.targetMultiplicity().lower());
        assertNull(composition.targetMultiplicity().upper());
    }

    @Test
    void rejectsDoctypeAndExternalEntityPayloadsFailClosed() {
        byte[] malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE xmi:XMI [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <xmi:XMI xmi:version="2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1">
                  <uml:Model name="&xxe;"/>
                </xmi:XMI>
                """.getBytes();

        XmiInterchangeException failure = assertThrows(
                XmiInterchangeException.class,
                () -> importer.importXmi(malicious)
        );
        assertEquals("XMI_INVALID_XML", failure.code());
    }

    @Test
    void rejectsUnsupportedXmiVersion() {
        byte[] unsupported = """
                <xmi:XMI xmi:version="1.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1">
                  <uml:Model name="Model"/>
                </xmi:XMI>
                """.getBytes();

        XmiInterchangeException failure = assertThrows(
                XmiInterchangeException.class,
                () -> importer.importXmi(unsupported)
        );
        assertEquals("XMI_VERSION_UNSUPPORTED", failure.code());
    }

    private long count(XmiImportResult result, UmlRelationshipType type) {
        return result.document().umlModel().relationships().stream()
                .filter(item -> item.type() == type)
                .count();
    }

    private byte[] fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/xmi/enterprise-architect-veterinaria.xmi")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }
}
