package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionHybridPromptBuilderTests {

    @Test
    void localRelationshipPromptFixesEndpointsAndPrefersOmissionOverHallucination() {
        VisionHybridPromptBuilder prompt = new VisionHybridPromptBuilder();
        String system = prompt.relationshipSystemPrompt();
        String user = prompt.relationshipUserPrompt(
                List.of(new VisionGeometryEdgeCandidate(
                        "E1", "B1", "B2", "c1", "c2", 0.7,
                        0, 0, 100, 100, 10, 10, 90, 90
                )),
                List.of(
                        new VisionClassProposal("c1", "Categoria", List.of(), null),
                        new VisionClassProposal("c2", "Libro", List.of(), null)
                )
        );
        assertTrue(system.contains("PAR FIJO"));
        assertTrue(system.contains("Precision antes que recall"));
        assertTrue(user.contains("E1"));
        assertTrue(user.contains("Categoria"));
        assertTrue(user.contains("Libro"));
    }
    @Test
    void mappingPromptForbidsCoordinatesAndUsesClosedGeometryIds() {
        VisionHybridPromptBuilder prompt = new VisionHybridPromptBuilder();
        String system = prompt.mappingSystemPrompt();
        String user = prompt.mappingUserPrompt(
                List.of(
                        new VisionGeometryClassRegion("B1", null, 10, 20, 100, 80, 0.9),
                        new VisionGeometryClassRegion("B2", null, 200, 20, 100, 80, 0.9)
                ),
                List.of(
                        new VisionClassProposal("c1", "Cliente", List.of(), null),
                        new VisionClassProposal("c2", "Factura", List.of(), null)
                )
        );
        assertTrue(system.contains("NO generes coordenadas"));
        assertTrue(system.contains("biyectivo"));
        assertTrue(user.contains("B1"));
        assertTrue(user.contains("c1"));
        assertTrue(user.contains("Cliente"));
    }

}
