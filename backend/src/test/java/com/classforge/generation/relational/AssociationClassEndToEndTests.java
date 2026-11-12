package com.classforge.generation.relational;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.vision.*;
import com.classforge.generation.relational.model.*;
import com.classforge.generation.spring.model.*;
import com.classforge.generation.spring.planning.*;
import com.classforge.generation.spring.validation.SpringGenerationModelValidator;
import com.classforge.integration.xmi.*;
import com.classforge.project.domain.document.*;
import com.classforge.project.validation.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AssociationClassEndToEndTests {
    private final RelationalModelMapper mapper =
            new RelationalModelMapper(new RelationalNamingStrategy(), new RelationalModelValidator());

    @Test
    void validatesAndMapsAssociationClassToOnePersistentEntity() {
        ProjectDocument document = document();
        assertDoesNotThrow(() -> new ProjectDocumentValidator().validate(document));

        RelationalModel relational = mapper.map(document.umlModel());
        assertEquals(3, relational.tables().size());
        assertEquals(2, relational.relations().size());
        assertTrue(relational.tables().stream().noneMatch(
                table -> table.origin().type() == RelationalTableOriginType.JOIN_RELATIONSHIP));

        RelationalTable join = relational.tables().stream()
                .filter(table -> table.logicalName().equals("ComiteTrabajo"))
                .findFirst().orElseThrow();
        assertEquals(RelationalTableOriginType.UML_CLASS, join.origin().type());
        assertEquals(List.of("id"), join.primaryKey().columnNames());
        assertEquals(2, join.foreignKeys().size());
        assertEquals(1, join.uniqueConstraints().size());
        assertEquals(2, join.uniqueConstraints().getFirst().columnNames().size());
        assertTrue(join.columns().stream().anyMatch(
                c -> c.name().equals("participacion") && c.origin() == RelationalColumnOrigin.ATTRIBUTE));
        assertTrue(join.columns().stream().anyMatch(
                c -> c.name().equals("fecha_inicio") && c.dataType() == RelationalDataType.DATE));

        SpringGenerationModel spring = new SpringGenerationPlanner(
                new SpringJavaNamingStrategy(), new SpringGenerationModelValidator()
        ).plan(relational, new SpringGenerationConfig("comunal-app", "com.classforge.comunal"));
        assertEquals(3, spring.entities().size());
        SpringEntityModel assocEntity = spring.entities().stream()
                .filter(e -> e.className().equals("ComiteTrabajo")).findFirst().orElseThrow();
        assertEquals(2, assocEntity.directRelations().size());
        assertTrue(assocEntity.manyToManyRelations().isEmpty());
    }

    @Test
    void rejectsNonManyToManyAndMissingAssociationClass() {
        ProjectDocument valid = document();
        UmlRelationship original = valid.umlModel().relationships().getFirst();
        UmlRelationship oneToMany = new UmlRelationship(
                original.id(), original.sourceClassId(), original.targetClassId(),
                UmlRelationshipType.ASSOCIATION, Multiplicity.one(),
                new Multiplicity(1, null), original.associationClassId());
        ProjectDocument invalid = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(valid.umlModel().classes(), List.of(oneToMany)), valid.layout());
        ProjectDocumentValidationException error = assertThrows(
                ProjectDocumentValidationException.class,
                () -> new ProjectDocumentValidator().validate(invalid));
        assertTrue(error.getViolations().stream().anyMatch(
                violation -> violation.code().equals("ASSOCIATION_CLASS_REQUIRES_MANY_TO_MANY")));

        UmlRelationship missing = new UmlRelationship(
                original.id(), original.sourceClassId(), original.targetClassId(),
                UmlRelationshipType.ASSOCIATION, original.sourceMultiplicity(),
                original.targetMultiplicity(), UUID.randomUUID());
        assertThrows(RelationalMappingException.class, () -> mapper.map(
                new UmlModel(valid.umlModel().classes(), List.of(missing))));
    }

    @Test
    void transcribesDashedVisualTetherIntoSemanticAssociationReference() {
        VisionEvidence evidence = new VisionEvidence("Asamblea Vocero ComiteTrabajo", 0.98,
                null, null, null, null);
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Asamblea de ciudadanos",
                List.of(
                        new VisionClassProposal("c1", "Asamblea", List.of(), evidenceFor("Asamblea")),
                        new VisionClassProposal("c2", "Vocero", List.of(), evidenceFor("Vocero")),
                        new VisionClassProposal("c3", "ComiteTrabajo",
                                List.of(new VisionAttributeProposal("participacion", "STRING",
                                        null, "PUBLIC", true, false, evidenceFor("participacion"))),
                                evidenceFor("ComiteTrabajo"))
                ),
                List.of(new VisionRelationshipProposal("c1", "c2", "ASSOCIATION",
                        new VisionMultiplicityProposal(0, null, true),
                        new VisionMultiplicityProposal(1, null, true),
                        evidence, "c3")),
                List.of(), 0.98);
        var compiled = new VisionProposalCompiler(
                new VisionCodeIdentifierCanonicalizer()).compile(proposal, ProjectDocument.empty());
        assertEquals(4, compiled.plan().actions().size());
        var association = compiled.plan().actions().getLast();
        assertEquals(AssistantActionType.CREATE_RELATIONSHIP, association.type());
        assertEquals("ComiteTrabajo", association.associationClassName());
        assertEquals(-1, association.sourceUpper());
        assertEquals(-1, association.targetUpper());
    }

    @Test
    void roundTripsAssociationClassAsUmlAssociationClassInXmi() {
        ProjectDocument before = document();
        var exporter = new EnterpriseArchitectXmiExporter();
        byte[] xmi = exporter.export(UUID.randomUUID(), "Consejo", before);
        String xml = new String(xmi, StandardCharsets.UTF_8);
        assertTrue(xml.contains("uml:AssociationClass"));
        assertTrue(xml.contains("ClassForgeRelationshipId:"));
        assertTrue(xml.contains("participacion"));
        ProjectDocument after = new EnterpriseArchitectXmiImporter().importXmi(xmi).document();
        new ProjectDocumentValidator().validate(after);
        assertEquals(before.umlModel(), after.umlModel());
    }

    private static VisionEvidence evidenceFor(String label) {
        return new VisionEvidence(label, 0.98, null, null, null, null);
    }

    private static ProjectDocument document() {
        UmlClass assembly = new UmlClass(UUID.randomUUID(), "Asamblea", List.of(
                attribute("id", UmlDataType.UUID, true)));
        UmlClass speaker = new UmlClass(UUID.randomUUID(), "Vocero", List.of(
                attribute("id", UmlDataType.UUID, true)));
        UmlClass committee = new UmlClass(UUID.randomUUID(), "ComiteTrabajo", List.of(
                attribute("participacion", UmlDataType.STRING, false),
                attribute("fechaInicio", UmlDataType.DATE, false)));
        UmlRelationship relationship = new UmlRelationship(
                UUID.randomUUID(), assembly.id(), speaker.id(), UmlRelationshipType.ASSOCIATION,
                new Multiplicity(0, null), new Multiplicity(1, null), committee.id());
        return new ProjectDocument(ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(assembly, speaker, committee), List.of(relationship)),
                new DiagramLayout(Map.of(
                        assembly.id(), DiagramNodeLayout.defaultForIndex(0),
                        speaker.id(), DiagramNodeLayout.defaultForIndex(1),
                        committee.id(), DiagramNodeLayout.defaultForIndex(2)
                )));
    }

    private static UmlAttribute attribute(String name, UmlDataType type, boolean identifier) {
        return new UmlAttribute(UUID.randomUUID(), name, type, null, UmlVisibility.PUBLIC,
                false, identifier);
    }
}
