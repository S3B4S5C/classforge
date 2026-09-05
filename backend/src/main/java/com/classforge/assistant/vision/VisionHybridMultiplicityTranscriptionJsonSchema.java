package com.classforge.assistant.vision;

public final class VisionHybridMultiplicityTranscriptionJsonSchema {

    private VisionHybridMultiplicityTranscriptionJsonSchema() {
    }

    public static String jsonForEndpoint(String edgeId, VisionHybridEndpoint endpoint) {
        return VisionHybridMultiplicityJsonSchema.jsonForMultiplicityEndpoint(edgeId, endpoint);
    }
}
