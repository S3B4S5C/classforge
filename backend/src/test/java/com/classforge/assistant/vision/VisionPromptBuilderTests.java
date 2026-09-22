package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionPromptBuilderTests {

    private final VisionPromptBuilder builder = new VisionPromptBuilder();

    @Test
    void systemPromptHardensNonUmlTypesMultiplicitiesAndGeneralization() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("NO_ACTIONABLE_UML:"));
        assertTrue(prompt.contains("UUID -> UUID"));
        assertTrue(prompt.contains("Boolean/bool -> BOOLEAN"));
        assertTrue(prompt.contains("sourceRef es la subclase"));
        assertTrue(prompt.contains("targetRef es la superclase"));
        assertTrue(prompt.contains("0..*"));
        assertTrue(prompt.contains("unbounded=true"));
        assertTrue(prompt.contains("No conviertas sustantivos de notas"));
    }

    @Test
    void systemPromptForbidsDomainCompletionAndDefinesEmptyCompartments() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("Un compartimento vacio significa cero atributos visibles"));
        assertTrue(prompt.contains("Nunca derives atributos a partir del nombre de la clase"));
        assertTrue(prompt.contains("Cliente NO implica nombre/email/telefono/dni"));
        assertTrue(prompt.contains("Pedido NO implica id/total"));
        assertTrue(prompt.contains("Libro NO implica isbn/titulo/añoPublicacion"));
        assertTrue(prompt.contains("No repitas el nombre de la clase como atributo"));
        assertTrue(prompt.contains("Si una caja UML muestra solo el encabezado de la clase"));
        assertTrue(prompt.contains("sigue siendo UML accionable"));
        assertTrue(prompt.contains("encabezado dice Cliente"));
    }

    @Test
    void systemPromptDistinguishesCompositionFromAggregationByDiamondFill() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("Rombo RELLENO/NEGRO (◆) => COMPOSITION"));
        assertTrue(prompt.contains("Rombo HUECO/BLANCO (◇) => AGGREGATION"));
        assertTrue(prompt.contains("Pedido ◆--- LineaPedido"));
        assertTrue(prompt.contains("Biblioteca ◇--- Libro"));
        assertTrue(prompt.contains("No decidas COMPOSITION frente a AGGREGATION por el significado del dominio"));
    }

    @Test
    void systemPromptForbidsTypeInferenceAndRequiresLiteralAttributeEvidence() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("Si NO hay tipo visible"));
        assertTrue(prompt.contains("No infieras DATE por nombres como fechaPrestamo/fechaDevolucion"));
        assertTrue(prompt.contains("añoPublicacion"));
        assertTrue(prompt.contains("evidence.label corto y literal"));
        assertTrue(prompt.contains("No prefixes el nombre de la clase"));
    }

    @Test
    void primaryPromptKeepsConservativeCal006TopologyRules() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("Topologia en diagramas densos"));
        assertTrue(prompt.contains("siguela fisicamente de extremo a extremo"));
        assertTrue(prompt.contains("Una linea que cruza otra no crea una union"));
        assertTrue(prompt.contains("segunda pasada revisa todas las cajas"));
        assertTrue(prompt.contains("segunda pasada exclusiva de multiplicidades"));
        assertTrue(prompt.contains("NO_ACTIONABLE_UML es exclusivo de una propuesta vacia"));
        assertFalse(prompt.contains("inventario de lineas consumidas"));
        assertFalse(prompt.contains("la relacion es A-C; NO declares A-B"));
    }


    @Test
    void promptAndSchemaTeachAssociationClassAsDashedConnectorToRelationship() {
        String prompt = builder.systemPrompt();
        String schema = VisionUmlProposalJsonSchema.json();

        assertTrue(prompt.contains("clase de asociacion"));
        assertTrue(prompt.contains("linea DISCONTINUA"));
        assertTrue(prompt.contains("associationClasses"));
        assertTrue(prompt.contains("NO es una relacion normal entre dos clases"));
        assertTrue(schema.contains("\"associationClasses\""));
        assertTrue(schema.contains("\"classRef\""));
        assertTrue(schema.contains("\"sourceRef\""));
        assertTrue(schema.contains("\"targetRef\""));
    }

    @Test
    void relationshipPassUsesClosedRefsAndFocusesOnlyOnPhysicalConnectors() {
        VisionUmlProposal firstPass = new VisionUmlProposal(
                "dense",
                List.of(
                        new VisionClassProposal("c1", "Categoria", List.of(), new VisionEvidence("Categoria", null, null, null, null, null)),
                        new VisionClassProposal("c2", "Libro", List.of(), new VisionEvidence("Libro", null, null, null, null, null)),
                        new VisionClassProposal("c3", "Usuario", List.of(), new VisionEvidence("Usuario", null, null, null, null, null)),
                        new VisionClassProposal("c4", "Prestamo", List.of(), new VisionEvidence("Prestamo", null, null, null, null, null))
                ),
                List.of(),
                List.of(),
                0.9
        );

        String system = builder.relationshipSystemPrompt();
        String user = builder.relationshipUserPrompt(firstPass, 1280, 720);

        assertTrue(system.contains("lista CERRADA"));
        assertTrue(system.contains("No declares clases ni atributos"));
        assertTrue(system.contains("cruce de lineas sin punto/nodo visible NO crea una conexion"));
        assertTrue(system.contains("Linea simple sin rombo ni triangulo => ASSOCIATION"));
        assertTrue(system.contains("nunca inventes 0..1"));
        assertTrue(user.contains("c1 = \"Categoria\""));
        assertTrue(user.contains("c4 = \"Prestamo\""));
        assertTrue(user.contains("1280x720"));
    }

    @Test
    void projectContextExposesNamesButNotDomainIdentifiers() {
        UUID projectId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID classId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        VisionProjectContext context = new VisionProjectContext(
                projectId,
                42L,
                List.of(new VisionExistingClassContext(
                        classId,
                        "Cliente",
                        List.of("email")
                ))
        );

        String prompt = builder.userPrompt(context, 640, 420);

        assertTrue(prompt.contains("Cliente"));
        assertTrue(prompt.contains("email"));
        assertTrue(prompt.contains("640x420"));
        assertFalse(prompt.contains(projectId.toString()));
        assertFalse(prompt.contains(classId.toString()));
    }
}
