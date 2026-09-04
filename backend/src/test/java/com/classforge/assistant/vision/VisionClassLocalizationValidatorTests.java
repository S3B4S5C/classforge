package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionClassLocalizationValidatorTests {

    private final VisionClassLocalizationValidator validator = new VisionClassLocalizationValidator();

    @Test
    void requiresExactClosedRefSetAndInBoundsBoxes() {
        List<VisionClassProposal> classes = List.of(
                new VisionClassProposal("c1", "Cliente", List.of(), new VisionEvidence("Cliente", 1.0, null, null, null, null)),
                new VisionClassProposal("c2", "Factura", List.of(), new VisionEvidence("Factura", 1.0, null, null, null, null))
        );
        VisionClassLocalizationProposal valid = new VisionClassLocalizationProposal(
                List.of(
                        new VisionClassLocalization("c1", 10, 10, 100, 80, 0.9),
                        new VisionClassLocalization("c2", 180, 20, 120, 90, 0.9)
                ),
                List.of(),
                0.9
        );
        assertDoesNotThrow(() -> validator.validate(valid, classes, 400, 200));

        VisionClassLocalizationProposal invented = new VisionClassLocalizationProposal(
                List.of(
                        new VisionClassLocalization("c1", 10, 10, 100, 80, 0.9),
                        new VisionClassLocalization("c9", 180, 20, 120, 90, 0.9)
                ),
                List.of(),
                0.9
        );
        assertThrows(AssistantPlanningException.class, () -> validator.validate(invented, classes, 400, 200));

        VisionClassLocalizationProposal overlapping = new VisionClassLocalizationProposal(
                List.of(
                        new VisionClassLocalization("c1", 10, 10, 160, 120, 0.9),
                        new VisionClassLocalization("c2", 35, 25, 160, 120, 0.9)
                ),
                List.of(),
                0.9
        );
        assertThrows(AssistantPlanningException.class, () -> validator.validate(overlapping, classes, 400, 200));
    }
}
