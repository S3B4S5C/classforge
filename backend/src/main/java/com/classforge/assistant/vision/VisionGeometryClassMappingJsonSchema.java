package com.classforge.assistant.vision;

public final class VisionGeometryClassMappingJsonSchema {

    private VisionGeometryClassMappingJsonSchema() {
    }

    public static String json() {
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["mappings","warnings","confidence"],
                  "properties":{
                    "mappings":{
                      "type":"array",
                      "items":{
                        "type":"object",
                        "additionalProperties":false,
                        "required":["geometryId","classRef","confidence"],
                        "properties":{
                          "geometryId":{"type":"string","minLength":1},
                          "classRef":{"type":"string","minLength":1},
                          "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                        }
                      }
                    },
                    "warnings":{"type":"array","items":{"type":"string"}},
                    "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                  }
                }
                """;
    }
}
