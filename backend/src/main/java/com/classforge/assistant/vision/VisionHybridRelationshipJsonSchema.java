package com.classforge.assistant.vision;

public final class VisionHybridRelationshipJsonSchema {

    private VisionHybridRelationshipJsonSchema() {
    }

    public static String json() {
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["edges","warnings","confidence"],
                  "properties":{
                    "edges":{
                      "type":"array",
                      "items":{
                        "type":"object",
                        "additionalProperties":false,
                        "required":["edgeId","type","markerAt","multiplicityA","multiplicityB","evidenceLabel","confidence"],
                        "properties":{
                          "edgeId":{"type":"string","minLength":1},
                          "type":{"type":"string","enum":["ASSOCIATION","AGGREGATION","COMPOSITION","GENERALIZATION"]},
                          "markerAt":{"type":"string","enum":["NONE","A","B"]},
                          "multiplicityA":{"$ref":"#/$defs/multiplicityOrNull"},
                          "multiplicityB":{"$ref":"#/$defs/multiplicityOrNull"},
                          "evidenceLabel":{"type":"string","minLength":1},
                          "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                        }
                      }
                    },
                    "warnings":{"type":"array","items":{"type":"string"}},
                    "confidence":{"type":["number","null"],"minimum":0,"maximum":1}
                  },
                  "$defs":{
                    "multiplicityOrNull":{
                      "anyOf":[
                        {"type":"null"},
                        {
                          "type":"object",
                          "additionalProperties":false,
                          "required":["lower","upper","unbounded"],
                          "properties":{
                            "lower":{"type":"integer","minimum":0},
                            "upper":{"type":["integer","null"],"minimum":0},
                            "unbounded":{"type":"boolean"}
                          }
                        }
                      ]
                    }
                  }
                }
                """;
    }
}
