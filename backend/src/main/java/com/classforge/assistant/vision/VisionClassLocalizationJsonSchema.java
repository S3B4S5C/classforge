package com.classforge.assistant.vision;

public final class VisionClassLocalizationJsonSchema {

    private VisionClassLocalizationJsonSchema() {
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
                        "required":["classRef","x","y","width","height","confidence"],
                        "properties":{
                          "classRef":{"type":"string","minLength":1},
                          "x":{"type":"integer","minimum":0},
                          "y":{"type":"integer","minimum":0},
                          "width":{"type":"integer","minimum":1},
                          "height":{"type":"integer","minimum":1},
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
