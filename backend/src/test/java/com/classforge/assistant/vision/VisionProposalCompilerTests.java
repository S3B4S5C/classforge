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
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionProposalCompilerTests {

    private final VisionProposalCompiler compiler =
            new VisionProposalCompiler(new VisionCodeIdentifierCanonicalizer());

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

    @Test
    void canonicalizesVisualIdentifiersOnlyAtCompilationBoundary() {
        VisionCompilationResult result = compiler.compile(new VisionUmlProposal(
                "Categoria visual",
                List.of(new VisionClassProposal(
                        "c1",
                        "Categoría",
                        List.of(
                                new VisionAttributeProposal(
                                        "añoPublicacion", "STRING", null, "PRIVATE", true, false,
                                        evidence("añoPublicacion")
                                ),
                                new VisionAttributeProposal(
                                        "dirección postal", "CUSTOM", "Dirección Postal", "PRIVATE", true, false,
                                        evidence("dirección postal")
                                )
                        ),
                        evidence("Categoría")
                )),
                List.of(),
                List.of(),
                0.9
        ), ProjectDocument.empty());

        var action = result.plan().actions().getFirst();
        assertEquals("Categoria", action.className());
        assertEquals("anoPublicacion", action.safeAttributes().get(0).name());
        assertEquals("Direccion_Postal", action.safeAttributes().get(1).customTypeName());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Categoría") && warning.contains("Categoria")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("añoPublicacion") && warning.contains("anoPublicacion")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Dirección Postal") && warning.contains("Direccion_Postal")));
    }

    @Test
    void reusesExistingCanonicalClassForAccentedVisualName() {
        ProjectDocument document = documentWithClass("Categoria", List.of());

        VisionCompilationResult result = compiler.compile(new VisionUmlProposal(
                "Categoria", List.of(new VisionClassProposal("c1", "Categoría", List.of(), evidence("Categoría"))),
                List.of(), List.of(), 0.9
        ), document);

        assertEquals(0, result.plan().actions().size());
    }

    @Test
    void reusesExistingCanonicalAttributeForAccentedVisualName() {
        ProjectDocument document = documentWithClass("Libro", List.of(new UmlAttribute(
                UUID.randomUUID(), "anoPublicacion", UmlDataType.STRING, null,
                UmlVisibility.PRIVATE, true, false
        )));

        VisionCompilationResult result = compiler.compile(new VisionUmlProposal(
                "Libro", List.of(new VisionClassProposal(
                        "l1", "Libro", List.of(new VisionAttributeProposal(
                                "añoPublicacion", "STRING", null, "PRIVATE", true, false,
                                evidence("añoPublicacion")
                        )), evidence("Libro")
                )), List.of(), List.of(), 0.9
        ), document);

        assertEquals(0, result.plan().actions().size());
    }

    @Test
    void rejectsClassCanonicalCollision() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "collision",
                List.of(
                        new VisionClassProposal("c1", "Categoría", List.of(), evidence("Categoría")),
                        new VisionClassProposal("c2", "Categoria", List.of(), evidence("Categoria"))
                ),
                List.of(), List.of(), 0.9
        );

        assertThrows(AssistantPlanningException.class, () -> compiler.compile(proposal, ProjectDocument.empty()));
    }

    @Test
    void rejectsAttributeCanonicalCollision() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "collision",
                List.of(new VisionClassProposal(
                        "c1", "Libro", List.of(
                                new VisionAttributeProposal("año", "STRING", null, "PRIVATE", true, false, evidence("año")),
                                new VisionAttributeProposal("ano", "STRING", null, "PRIVATE", true, false, evidence("ano"))
                        ), evidence("Libro")
                )),
                List.of(), List.of(), 0.9
        );

        assertThrows(AssistantPlanningException.class, () -> compiler.compile(proposal, ProjectDocument.empty()));
    }


    @Test
    void compilesDashedAssociationClassEvidenceIntoCanonicalAssociationClassAction() {
        VisionUmlProposal proposal = new VisionUmlProposal(
                "Pedido con detalle",
                List.of(
                        new VisionClassProposal("pedido", "Pedido", List.of(), evidence("Pedido")),
                        new VisionClassProposal("producto", "Producto", List.of(), evidence("Producto")),
                        new VisionClassProposal(
                                "detalle", "DetallePedido",
                                List.of(
                                        new VisionAttributeProposal(
                                                "cantidad", "INTEGER", null, "PUBLIC", false, false,
                                                evidence("cantidad : int")
                                        ),
                                        new VisionAttributeProposal(
                                                "precioUnitario", "DECIMAL", null, "PUBLIC", false, false,
                                                evidence("precioUnitario : Decimal")
                                        )
                                ),
                                evidence("DetallePedido")
                        )
                ),
                List.of(new VisionRelationshipProposal(
                        "pedido", "producto", "COMPOSITION",
                        new VisionMultiplicityProposal(1, null, true),
                        new VisionMultiplicityProposal(1, null, true),
                        evidence("Pedido Producto")
                )),
                List.of(new VisionAssociationClassProposal(
                        "detalle", "pedido", "producto", evidence("dashed connector")
                )),
                List.of(),
                0.96
        );

        VisionCompilationResult result = compiler.compile(proposal, ProjectDocument.empty());

        assertEquals(3, result.plan().actions().size());
        assertEquals(AssistantActionType.CREATE_CLASS, result.plan().actions().get(0).type());
        assertEquals(AssistantActionType.CREATE_CLASS, result.plan().actions().get(1).type());
        var associationClass = result.plan().actions().get(2);
        assertEquals(AssistantActionType.CREATE_ASSOCIATION_CLASS, associationClass.type());
        assertEquals("DetallePedido", associationClass.className());
        assertEquals("Pedido", associationClass.sourceClassName());
        assertEquals("Producto", associationClass.targetClassName());
        assertEquals(UmlRelationshipType.COMPOSITION, associationClass.relationshipType());
        assertEquals(1, associationClass.sourceLower());
        assertEquals(-1, associationClass.sourceUpper());
        assertEquals(1, associationClass.targetLower());
        assertEquals(-1, associationClass.targetUpper());
        assertEquals(
                List.of("cantidad:INTEGER", "precioUnitario:DECIMAL"),
                associationClass.safeAttributes().stream()
                        .map(attribute -> attribute.name() + ":" + attribute.dataType().name())
                        .sorted().toList()
        );
        assertTrue(result.plan().actions().stream()
                .noneMatch(action -> action.type() == AssistantActionType.CREATE_RELATIONSHIP));
    }

    private ProjectDocument documentWithClass(String name, List<UmlAttribute> attributes) {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(new UmlClass(UUID.randomUUID(), name, attributes)), List.of()),
                new DiagramLayout(Map.of())
        );
    }

    private VisionEvidence evidence(String label) {
        return new VisionEvidence(label, 0.9, 1, 1, 100, 40);
    }
}
