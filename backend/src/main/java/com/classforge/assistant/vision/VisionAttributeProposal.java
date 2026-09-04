package com.classforge.assistant.vision;

public record VisionAttributeProposal(
        String name,
        String dataType,
        String customTypeName,
        String visibility,
        Boolean nullable,
        Boolean identifier,
        VisionEvidence evidence
) {
}
