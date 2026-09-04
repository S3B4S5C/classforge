package com.classforge.assistant.vision;

public interface VisionModelGateway {

    VisionUmlProposal analyze(
            VisionNormalizedImage image,
            VisionProjectContext context
    );
}
