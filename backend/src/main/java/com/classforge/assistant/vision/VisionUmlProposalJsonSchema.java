package com.classforge.assistant.vision;

public final class VisionUmlProposalJsonSchema {

    private VisionUmlProposalJsonSchema() {
    }

    public static String json() {
        return """
                {
                  "title": "VisionUmlProposal",
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "summary": {
                      "type": "string",
                      "maxLength": 300
                    },
                    "classes": {
                      "type": "array",
                      "maxItems": 40,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": [
                          "ref",
                          "name",
                          "attributes",
                          "evidence"
                        ],
                        "properties": {
                          "ref": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 80
                          },
                          "name": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "attributes": {
                            "type": "array",
                            "maxItems": 80,
                            "items": {
                              "type": "object",
                              "additionalProperties": false,
                              "required": [
                                "name",
                                "evidence"
                              ],
                              "properties": {
                                "name": {
                                  "type": "string",
                                  "minLength": 1,
                                  "maxLength": 120
                                },
                                "dataType": {
                                  "type": "string",
                                  "enum": [
                                    "STRING",
                                    "INTEGER",
                                    "LONG",
                                    "DECIMAL",
                                    "BOOLEAN",
                                    "DATE",
                                    "DATETIME",
                                    "UUID",
                                    "CUSTOM"
                                  ]
                                },
                                "customTypeName": {
                                  "type": "string",
                                  "minLength": 1,
                                  "maxLength": 120
                                },
                                "visibility": {
                                  "type": "string",
                                  "enum": [
                                    "PUBLIC",
                                    "PRIVATE",
                                    "PROTECTED",
                                    "PACKAGE"
                                  ]
                                },
                                "nullable": {
                                  "type": "boolean"
                                },
                                "identifier": {
                                  "type": "boolean"
                                },
                                "evidence": {
                                  "type": "object",
                                  "additionalProperties": false,
                                  "required": [
                                    "label"
                                  ],
                                  "properties": {
                                    "label": {
                                      "type": "string",
                                      "minLength": 1,
                                      "maxLength": 240
                                    },
                                    "confidence": {
                                      "type": "number",
                                      "minimum": 0,
                                      "maximum": 1
                                    },
                                    "x": {
                                      "type": "integer",
                                      "minimum": 0
                                    },
                                    "y": {
                                      "type": "integer",
                                      "minimum": 0
                                    },
                                    "width": {
                                      "type": "integer",
                                      "minimum": 1
                                    },
                                    "height": {
                                      "type": "integer",
                                      "minimum": 1
                                    }
                                  }
                                }
                              }
                            }
                          },
                          "evidence": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": [
                              "label"
                            ],
                            "properties": {
                              "label": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": 240
                              },
                              "confidence": {
                                "type": "number",
                                "minimum": 0,
                                "maximum": 1
                              },
                              "x": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "y": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "width": {
                                "type": "integer",
                                "minimum": 1
                              },
                              "height": {
                                "type": "integer",
                                "minimum": 1
                              }
                            }
                          }
                        }
                      }
                    },
                    "relationships": {
                      "type": "array",
                      "maxItems": 80,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": [
                          "sourceRef",
                          "targetRef",
                          "type",
                          "evidence"
                        ],
                        "properties": {
                          "sourceRef": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "targetRef": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "type": {
                            "type": "string",
                            "enum": [
                              "ASSOCIATION",
                              "AGGREGATION",
                              "COMPOSITION",
                              "GENERALIZATION"
                            ]
                          },
                          "sourceMultiplicity": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "lower": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "upper": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "unbounded": {
                                "type": "boolean"
                              }
                            }
                          },
                          "targetMultiplicity": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "lower": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "upper": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "unbounded": {
                                "type": "boolean"
                              }
                            }
                          },
                          "evidence": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": [
                              "label"
                            ],
                            "properties": {
                              "label": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": 240
                              },
                              "confidence": {
                                "type": "number",
                                "minimum": 0,
                                "maximum": 1
                              },
                              "x": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "y": {
                                "type": "integer",
                                "minimum": 0
                              },
                              "width": {
                                "type": "integer",
                                "minimum": 1
                              },
                              "height": {
                                "type": "integer",
                                "minimum": 1
                              }
                            }
                          }
                        }
                      }
                    },
                    "associationClasses": {
                      "type": "array",
                      "maxItems": 40,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": [
                          "classRef",
                          "sourceRef",
                          "targetRef",
                          "evidence"
                        ],
                        "properties": {
                          "classRef": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "sourceRef": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "targetRef": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 120
                          },
                          "evidence": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": ["label"],
                            "properties": {
                              "label": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": 240
                              },
                              "confidence": {
                                "type": "number",
                                "minimum": 0,
                                "maximum": 1
                              },
                              "x": { "type": "integer", "minimum": 0 },
                              "y": { "type": "integer", "minimum": 0 },
                              "width": { "type": "integer", "minimum": 1 },
                              "height": { "type": "integer", "minimum": 1 }
                            }
                          }
                        }
                      }
                    },
                    "warnings": {
                      "type": "array",
                      "maxItems": 30,
                      "items": {
                        "type": "string",
                        "maxLength": 240
                      }
                    },
                    "confidence": {
                      "type": "number",
                      "minimum": 0,
                      "maximum": 1
                    }
                  },
                  "required": [
                    "classes",
                    "relationships",
                    "associationClasses",
                    "warnings"
                  ]
                }
                """;
    }
}
