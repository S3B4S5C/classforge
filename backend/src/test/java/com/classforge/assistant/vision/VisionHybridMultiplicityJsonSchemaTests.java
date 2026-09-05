package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionHybridMultiplicityJsonSchemaTests {

    @Test
    void endpointSchemaIsClosedAndBounded() throws Exception {
        JsonNode schema = JsonMapper.builder().build().readTree(
                VisionHybridMultiplicityTranscriptionJsonSchema.jsonForEndpoint("E7", VisionHybridEndpoint.B)
        );
        JsonNode properties = schema.get("properties");
        JsonNode rawLabel = properties.get("rawLabel");

        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals("E7", properties.get("edgeId").get("enum").get(0).asString());
        assertEquals("B", properties.get("endpoint").get("enum").get(0).asString());
        assertEquals(16, rawLabel.get("maxLength").asInt());
        assertEquals("string", rawLabel.get("type").get(0).asString());
        assertEquals("null", rawLabel.get("type").get(1).asString());
        assertFalse(schema.toString().contains("array"));
        assertFalse(schema.get("properties").has("warnings"));
    }

    @Test
    void attributionSchemaIsClosedAndUsesDeterministicOwnerEnum() throws Exception {
        JsonNode schema = JsonMapper.builder().build().readTree(
                VisionHybridMultiplicityAttributionJsonSchema.jsonForMultiplicityAttribution(
                        "E6", VisionHybridEndpoint.B, java.util.List.of("E4", "E2", "E4")
                )
        );
        JsonNode properties = schema.get("properties");

        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals("E6", properties.get("edgeId").get("enum").get(0).asString());
        assertEquals("B", properties.get("endpoint").get("enum").get(0).asString());
        assertEquals("E6", properties.get("owner").get("enum").get(0).asString());
        assertEquals("E2", properties.get("owner").get("enum").get(1).asString());
        assertEquals("E4", properties.get("owner").get("enum").get(2).asString());
        assertEquals("AMBIGUOUS", properties.get("owner").get("enum").get(3).asString());
        assertEquals("NONE", properties.get("owner").get("enum").get(4).asString());
        assertFalse(schema.toString().contains("warnings"));
        assertFalse(schema.toString().contains("array"));
    }
}
