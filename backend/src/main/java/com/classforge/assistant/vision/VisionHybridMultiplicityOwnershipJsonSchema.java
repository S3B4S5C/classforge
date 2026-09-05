package com.classforge.assistant.vision;

public final class VisionHybridMultiplicityOwnershipJsonSchema {

    private VisionHybridMultiplicityOwnershipJsonSchema() {
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
                  "required":["edgeId","endpoint","ownership","confidence"],
                  "properties":{
                    "edgeId":{"type":"string","enum":["%s"]},
                    "endpoint":{"type":"string","enum":["%s"]},
                    "ownership":{"type":"string","enum":["BELONGS","NOT_BELONGS","AMBIGUOUS"]},
                    "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                  }
                }
                """.formatted(escapedEdgeId, endpoint.name());
    }
}
