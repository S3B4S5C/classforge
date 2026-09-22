package com.classforge.assistant.bedrock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockDocumentCodecTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void roundTripsNestedJsonWithoutLosingShape() {
        Map<String, Object> source = Map.of(
                "name", "Cliente",
                "active", true,
                "count", 3,
                "items", List.of("a", "b"),
                "nested", Map.of("confidence", 0.75)
        );

        Document document = BedrockDocumentCodec.fromObject(source);
        JsonNode json = BedrockDocumentCodec.toJsonNode(jsonMapper, document);

        assertEquals("Cliente", json.get("name").asString());
        assertTrue(json.get("active").asBoolean());
        assertEquals(3, json.get("count").asInt());
        assertEquals("b", json.get("items").get(1).asString());
        assertEquals(0.75, json.get("nested").get("confidence").asDouble(), 0.0001);
    }

    @Test
    void acceptsJsonSchemaTextAsBedrockDocument() {
        Document schema = BedrockDocumentCodec.fromJson(
                jsonMapper,
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}"
        );

        assertTrue(schema.isMap());
        assertEquals("object", schema.asMap().get("type").asString());
    }
}
