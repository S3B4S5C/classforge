package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionHybridPromptBuilderTests {

    @Test
    void localRelationshipPromptClassifiesGeometryConfirmedTopology() {
        VisionHybridPromptBuilder prompt = new VisionHybridPromptBuilder();
        String system = prompt.relationshipSystemPrompt();
        String user = prompt.relationshipUserPrompt(
                new VisionGeometryEdgeCandidate(
                        "E1", "B1", "B2", "c1", "c2", 0.7,
                        0, 0, 100, 100, 10, 10, 90, 90
                ),
                List.of(
                        new VisionClassProposal("c1", "Categoria", List.of(), null),
                        new VisionClassProposal("c2", "Libro", List.of(), null)
                )
        );
        assertTrue(system.contains("unico panel"));
        assertTrue(system.contains("OpenCV/Java ya confirmo"));
        assertTrue(system.contains("maximo 12 palabras"));
        assertTrue(system.contains("warnings=[]"));
        assertTrue(system.contains("No expliques razonamiento"));
        assertTrue(system.contains("describas el proceso"));
        assertFalse(system.contains("omite ese edge"));
        assertFalse(system.contains("edges=[]"));
        assertTrue(user.contains("E1"));
        assertTrue(user.contains("Categoria"));
        assertTrue(user.contains("Libro"));
        assertTrue(user.contains("No cambies ni omitas endpoints"));
    }

    @Test
    void multiplicityPromptSeparatesCleanTranscriptionFromGuidedOwnership() {
        String system = new VisionHybridPromptBuilder().multiplicityTranscriptionSystemPrompt();

        assertTrue(system.contains("unica tarea es LEER"));
        assertTrue(system.contains("No decidas a que connector pertenece"));
        assertFalse(system.contains("ownership"));
        assertTrue(system.contains("rawLabel=null"));
    }

    @Test
    void ownershipPromptVerifiesButDoesNotRetranscribeCandidate() {
        VisionHybridPromptBuilder prompt = new VisionHybridPromptBuilder();
        String system = prompt.multiplicityOwnershipSystemPrompt();
        String user = prompt.multiplicityOwnershipUserPrompt(
                new VisionGeometryEdgeCandidate("E1", "B1", "B2", "c1", "c2", 0.7, 0, 0, 100, 100, 10, 10, 90, 90),
                VisionHybridEndpoint.A, new VisionClassProposal("c1", "Libro", List.of(), null), "0..*"
        );

        assertTrue(system.contains("NO lo retranscribas"));
        assertTrue(system.contains("LABEL SOURCE"));
        assertTrue(system.contains("CLASS CONTEXT"));
        assertTrue(system.contains("AMBIGUOUS"));
        assertTrue(system.contains("edge ID propietario"));
        assertTrue(user.contains("candidateRawLabel=\"0..*\""));
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
