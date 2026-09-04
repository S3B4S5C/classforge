package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionProposalCompilerTests {

    private final VisionProposalCompiler compiler = new VisionProposalCompiler();

    @Test
    void compilesNewClassesAttributesAndRelationshipIntoExistingSemanticIr() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Crear Cliente y Factura",
                List.of(
                        new VisionClassProposal(
                                "c1", "Cliente",
                                List.of(
                                        new VisionAttributeProposal(
                                                "email", "STRING", null, "PRIVATE", true, false, evidence("email")
                                        )
                                ),
                                evidence("Cliente")
                        ),
                        new VisionClassProposal("c2", "Factura", List.of(), evidence("Factura"))
                ),
                List.of(
                        new VisionRelationshipProposal(
                                "c1", "c2", "ASSOCIATION",
                                new VisionMultiplicityProposal(1, 1, false),
                                new VisionMultiplicityProposal(0, null, true),
                                evidence("Cliente Factura")
                        )
                ),
                List.of("revisar nombres"),
                0.91
        );

        VisionCompilationResult result = compiler.compile(proposal, ProjectDocument.empty());

        assertEquals(3, result.plan().actions().size());
        assertEquals(AssistantActionType.CREATE_CLASS, result.plan().actions().get(0).type());
        assertEquals("Cliente", result.plan().actions().get(0).className());
        assertEquals("email", result.plan().actions().get(0).safeAttributes().getFirst().name());
        assertEquals(AssistantActionType.CREATE_RELATIONSHIP, result.plan().actions().get(2).type());
        assertEquals("Cliente", result.plan().actions().get(2).sourceClassName());
        assertEquals("Factura", result.plan().actions().get(2).targetClassName());
        assertEquals(-1, result.plan().actions().get(2).targetUpper());
    }

    @Test
    void reusesExactExistingClassAndOnlyAddsNewAttributes() {
        UmlClass mascota = new UmlClass(
                UUID.randomUUID(),
                "Mascota",
                List.of(
                        new UmlAttribute(
                                UUID.randomUUID(), "nombre", UmlDataType.STRING, null,
                                UmlVisibility.PRIVATE, true, false
                        )
                )
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(mascota), List.of()),
                new DiagramLayout(Map.of())
        );

        VisionUmlProposal proposal = new VisionUmlProposal(
                "Completar Mascota",
                List.of(
                        new VisionClassProposal(
                                "m1", "mascota",
                                List.of(
                                        new VisionAttributeProposal("nombre", "STRING", null, null, true, false, evidence("nombre")),
                                        new VisionAttributeProposal("peso", "DECIMAL", null, null, true, false, evidence("peso"))
                                ),
                                evidence("Mascota")
                        )
                ),
                List.of(),
                List.of(),
                0.88
        );

        VisionCompilationResult result = compiler.compile(proposal, document);

        assertEquals(1, result.plan().actions().size());
        assertEquals(AssistantActionType.ADD_ATTRIBUTES, result.plan().actions().getFirst().type());
        assertEquals("Mascota", result.plan().actions().getFirst().className());
        assertEquals(1, result.plan().actions().getFirst().safeAttributes().size());
        assertEquals("peso", result.plan().actions().getFirst().safeAttributes().getFirst().name());
    }

    @Test
    void identicalExistingAttributeProducesNoChanges() {
        UmlClass cliente = new UmlClass(
                UUID.randomUUID(),
                "Cliente",
                List.of(new UmlAttribute(
                        UUID.randomUUID(), "email", UmlDataType.STRING, null,
                        UmlVisibility.PRIVATE, true, false
                ))
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(cliente), List.of()),
                new DiagramLayout(Map.of())
        );
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Cliente",
                List.of(new VisionClassProposal(
                        "c1", "Cliente",
                        List.of(new VisionAttributeProposal(
                                "email", "STRING", null, "PRIVATE", true, false, evidence("email")
                        )),
                        evidence("Cliente")
                )),
                List.of(),
                List.of(),
                0.9
        );

        VisionCompilationResult result = compiler.compile(proposal, document);

        assertEquals(0, result.plan().actions().size());
    }

    @Test
    void conflictingExistingAttributeTypeIsWarningAndNeverAutoUpdated() {
        UmlClass cliente = new UmlClass(
                UUID.randomUUID(),
                "Cliente",
                List.of(new UmlAttribute(
                        UUID.randomUUID(), "email", UmlDataType.STRING, null,
                        UmlVisibility.PRIVATE, true, false
                ))
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(cliente), List.of()),
                new DiagramLayout(Map.of())
        );
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Cliente",
                List.of(new VisionClassProposal(
                        "c1", "Cliente",
                        List.of(new VisionAttributeProposal(
                                "email", "INTEGER", null, "PRIVATE", true, false, evidence("email")
                        )),
                        evidence("Cliente")
                )),
                List.of(),
                List.of(),
                0.9
        );

        VisionCompilationResult result = compiler.compile(proposal, document);

        assertEquals(0, result.plan().actions().size());
        assertEquals(true, result.warnings().stream().anyMatch(value -> value.contains("contradice")));
    }

    @Test
    void existingRelationshipIsNotDuplicated() {
        UmlClass cliente = new UmlClass(UUID.randomUUID(), "Cliente", List.of());
        UmlClass factura = new UmlClass(UUID.randomUUID(), "Factura", List.of());
        UmlRelationship relationship = new UmlRelationship(
                UUID.randomUUID(),
                cliente.id(),
                factura.id(),
                UmlRelationshipType.ASSOCIATION,
                new Multiplicity(1, 1),
                new Multiplicity(0, null)
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(cliente, factura), List.of(relationship)),
                new DiagramLayout(Map.of())
        );
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Relacion existente",
                List.of(
                        new VisionClassProposal("c1", "Cliente", List.of(), evidence("Cliente")),
                        new VisionClassProposal("c2", "Factura", List.of(), evidence("Factura"))
                ),
                List.of(new VisionRelationshipProposal(
                        "c1", "c2", "ASSOCIATION",
                        new VisionMultiplicityProposal(1, 1, false),
                        new VisionMultiplicityProposal(0, null, true),
                        evidence("Cliente Factura")
                )),
                List.of(),
                0.9
        );

        VisionCompilationResult result = compiler.compile(proposal, document);

        assertEquals(0, result.plan().actions().size());
        assertEquals(true, result.warnings().stream().anyMatch(value -> value.contains("ya existe")));
    }

    @Test
    void rejectsRelationshipToUnknownVisualReference() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "bad",
                List.of(new VisionClassProposal("c1", "Cliente", List.of(), evidence("Cliente"))),
                List.of(new VisionRelationshipProposal("c1", "ghost", "ASSOCIATION", null, null, evidence("Cliente ghost"))),
                List.of(),
                0.5
        );

        assertThrows(
                AssistantPlanningException.class,
                () -> compiler.compile(proposal, ProjectDocument.empty())
        );
    }

    private VisionEvidence evidence(String label) {
        return new VisionEvidence(label, 0.9, 1, 1, 100, 40);
    }
}
