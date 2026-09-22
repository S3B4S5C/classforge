package com.classforge.assistant.bedrock;

import software.amazon.awssdk.core.document.Document;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BedrockDocumentCodec {

    private BedrockDocumentCodec() {
    }

    public static Document fromJson(JsonMapper jsonMapper, String json) {
        try {
            Object value = jsonMapper.readValue(json, Object.class);
            return fromObject(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("No se pudo convertir el JSON Schema al formato Document de Bedrock.", exception);
        }
    }

    public static Document fromObject(Object value) {
        if (value == null) {
            return Document.fromNull();
        }
        if (value instanceof Document document) {
            return document;
        }
        if (value instanceof String string) {
            return Document.fromString(string);
        }
        if (value instanceof Boolean bool) {
            return Document.fromBoolean(bool);
        }
        if (value instanceof Number number) {
            return Document.fromNumber(number.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Document> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), fromObject(entry.getValue()));
            }
            return Document.fromMap(converted);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Document> converted = new ArrayList<>();
            for (Object item : iterable) {
                converted.add(fromObject(item));
            }
            return Document.fromList(converted);
        }
        throw new IllegalArgumentException("Tipo JSON no soportado por Bedrock Document: " + value.getClass().getName());
    }

    public static JsonNode toJsonNode(JsonMapper jsonMapper, Document document) {
        return jsonMapper.valueToTree(toObject(document));
    }

    public static Object toObject(Document document) {
        if (document == null || document.isNull()) {
            return null;
        }
        if (document.isBoolean()) {
            return document.asBoolean();
        }
        if (document.isString()) {
            return document.asString();
        }
        if (document.isNumber()) {
            return new BigDecimal(document.asNumber().stringValue());
        }
        if (document.isList()) {
            return document.asList().stream().map(BedrockDocumentCodec::toObject).toList();
        }
        if (document.isMap()) {
            Map<String, Object> converted = new LinkedHashMap<>();
            document.asMap().forEach((key, value) -> converted.put(key, toObject(value)));
            return converted;
        }
        throw new IllegalArgumentException("Tipo Document de Bedrock no soportado.");
    }
}
