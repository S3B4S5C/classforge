package com.classforge.assistant.vision;

public final class VisionHybridMultiplicityTranscriptionJsonSchema {

    private VisionHybridMultiplicityTranscriptionJsonSchema() {
    }

    public static String jsonForEndpoint(String edgeId, VisionHybridEndpoint endpoint) {
        if (edgeId == null || edgeId.isBlank() || endpoint == null) {
            throw new IllegalArgumentException("edgeId and endpoint are required");
        }
        String escapedEdgeId = edgeId.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["edgeId","endpoint","rawLabel","confidence"],
                  "properties":{
                    "edgeId":{"type":"string","enum":["%s"]},
                    "endpoint":{"type":"string","enum":["%s"]},
                    "rawLabel":{"type":"string","minLength":1,"maxLength":16},
                    "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                  }
                }
                """.formatted(escapedEdgeId, endpoint.name());
    }
}
