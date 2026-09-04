package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionEvidenceBoundsValidatorTests {

    private final VisionEvidenceBoundsValidator validator = new VisionEvidenceBoundsValidator();

    @Test
    void acceptsEvidenceInsideNormalizedImage() {
        VisionUmlProposal proposal = proposal(new VisionEvidence("Cliente", 0.9, 10, 20, 100, 50));
        assertDoesNotThrow(() -> validator.validate(proposal, 320, 200));
    }

    @Test
    void rejectsEvidenceOutsideNormalizedImage() {
        VisionUmlProposal proposal = proposal(new VisionEvidence("Cliente", 0.9, 250, 20, 100, 50));
        assertThrows(
                AssistantPlanningException.class,
                () -> validator.validate(proposal, 320, 200)
        );
    }

    private VisionUmlProposal proposal(VisionEvidence evidence) {
        return new VisionUmlProposal(
                "Cliente",
                List.of(new VisionClassProposal("c1", "Cliente", List.of(), evidence)),
                List.of(),
                List.of(),
                0.9
        );
    }
}
