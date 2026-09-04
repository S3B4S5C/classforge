package com.classforge.assistant.vision;

public final class VisionRelationshipPassJsonSchema {

    private VisionRelationshipPassJsonSchema() {
    }

    public static String json() {
        return """
                {
                  "title": "VisionRelationshipPassProposal",
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "relationships": {
                      "type": "array",
                      "maxItems": 80,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["sourceRef", "targetRef", "type", "evidence"],
                        "properties": {
                          "sourceRef": {"type": "string", "minLength": 1, "maxLength": 120},
                          "targetRef": {"type": "string", "minLength": 1, "maxLength": 120},
                          "type": {
                            "type": "string",
                            "enum": ["ASSOCIATION", "AGGREGATION", "COMPOSITION", "GENERALIZATION"]
                          },
                          "sourceMultiplicity": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "lower": {"type": "integer", "minimum": 0},
                              "upper": {"type": "integer", "minimum": 0},
                              "unbounded": {"type": "boolean"}
                            }
                          },
                          "targetMultiplicity": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "lower": {"type": "integer", "minimum": 0},
                              "upper": {"type": "integer", "minimum": 0},
                              "unbounded": {"type": "boolean"}
                            }
                          },
                          "evidence": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": ["label"],
                            "properties": {
                              "label": {"type": "string", "minLength": 1, "maxLength": 240},
                              "confidence": {"type": "number", "minimum": 0, "maximum": 1},
                              "x": {"type": "integer", "minimum": 0},
                              "y": {"type": "integer", "minimum": 0},
                              "width": {"type": "integer", "minimum": 1},
                              "height": {"type": "integer", "minimum": 1}
                            }
                          }
                        }
                      }
                    },
                    "warnings": {
                      "type": "array",
                      "maxItems": 30,
                      "items": {"type": "string", "maxLength": 240}
                    },
                    "confidence": {"type": "number", "minimum": 0, "maximum": 1}
                  },
                  "required": ["relationships", "warnings"]
                }
                """;
    }
}
