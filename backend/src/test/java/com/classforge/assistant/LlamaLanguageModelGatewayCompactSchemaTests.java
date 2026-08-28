package com.classforge.assistant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlamaLanguageModelGatewayCompactSchemaTests {

    @Test
    void richIntentSchemaDoesNotRequireNullPadding() {
        LlamaLanguageModelGateway gateway =
                new LlamaLanguageModelGateway(
                        JsonMapper.builder()
                                .build(),
                        "http://127.0.0.1:8092",
                        "local-model"
                );

        Map<String, Object> root =
                gateway.schema();

        Map<String, Object> rootProperties =
                map(
                        root.get(
                                "properties"
                        )
                );

        Map<String, Object> actions =
                map(
                        rootProperties.get(
                                "actions"
                        )
                );

        Map<String, Object> actionSchema =
                map(
                        actions.get(
                                "items"
                        )
                );

        assertEquals(
                List.of(
                        "type"
                ),
                actionSchema.get(
                        "required"
                )
        );

        Map<String, Object> actionProperties =
                map(
                        actionSchema.get(
                                "properties"
                        )
                );

        Map<String, Object> attributes =
                map(
                        actionProperties.get(
                                "attributes"
                        )
                );

        Map<String, Object> attributeSchema =
                map(
                        attributes.get(
                                "items"
                        )
                );

        assertEquals(
                List.of(
                        "name"
                ),
                attributeSchema.get(
                        "required"
                )
        );
    }

    @Test
    void localRuntimeHasEnoughTimeForFourBModel() {
        assertEquals(
                Duration.ofSeconds(90),
                LlamaLanguageModelGateway.REQUEST_TIMEOUT
        );

        assertEquals(
                256,
                LlamaLanguageModelGateway.MAX_COMPLETION_TOKENS
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(
            Object value
    ) {
        return (Map<String, Object>) value;
    }
}