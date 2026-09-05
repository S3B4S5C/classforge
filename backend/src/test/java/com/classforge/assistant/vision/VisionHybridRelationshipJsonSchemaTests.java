package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisionHybridRelationshipJsonSchemaTests {

    @Test
    void singletonSchemaBoundsFreeFormRelationshipOutput() throws Exception {
        JsonNode schema = JsonMapper.builder().build().readTree(
                VisionHybridRelationshipJsonSchema.jsonForEdge("E7")
        );
        JsonNode properties = schema.get("properties");
        JsonNode edges = properties.get("edges");
        JsonNode edge = edges.get("items").get("properties");
        JsonNode warnings = properties.get("warnings");

        assertEquals(1, edges.get("minItems").asInt());
        assertEquals(1, edges.get("maxItems").asInt());
        assertEquals("E7", edge.get("edgeId").get("enum").get(0).asString());
        assertEquals(120, edge.get("evidenceLabel").get("maxLength").asInt());
        assertEquals(1, warnings.get("maxItems").asInt());
        assertEquals(160, warnings.get("items").get("maxLength").asInt());
    }
}
