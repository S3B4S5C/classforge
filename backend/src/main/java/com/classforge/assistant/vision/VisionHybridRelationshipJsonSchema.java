package com.classforge.assistant.vision;

public final class VisionHybridRelationshipJsonSchema {

    private VisionHybridRelationshipJsonSchema() {
    }

    public static String jsonForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId is required");
        }
        String escapedEdgeId = edgeId.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["edges","warnings","confidence"],
                  "properties":{
                    "edges":{
                      "type":"array",
                      "minItems":1,
                      "maxItems":1,
                      "items":{
                        "type":"object",
                        "additionalProperties":false,
                        "required":["edgeId","type","markerAt","evidenceLabel","confidence"],
                        "properties":{
                          "edgeId":{"type":"string","enum":["%s"]},
                          "type":{"type":"string","enum":["ASSOCIATION","AGGREGATION","COMPOSITION","GENERALIZATION"]},
                          "markerAt":{"type":"string","enum":["NONE","A","B"]},
                          "evidenceLabel":{"type":"string","minLength":1,"maxLength":120},
                          "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                        }
                      }
                    },
                    "warnings":{"type":"array","maxItems":1,"items":{"type":"string","maxLength":160}},
                    "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                  }
                }
                """.formatted(escapedEdgeId);
    }
}
