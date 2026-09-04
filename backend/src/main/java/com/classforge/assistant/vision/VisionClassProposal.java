package com.classforge.assistant.vision;

import java.util.List;

public record VisionClassProposal(
        String ref,
        String name,
        List<VisionAttributeProposal> attributes,
        VisionEvidence evidence
) {
    public List<VisionAttributeProposal> safeAttributes() {
        return attributes == null ? List.of() : List.copyOf(attributes);
    }
}
