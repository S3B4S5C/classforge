package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaLanguageModelGatewayIntentHintTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final LlamaLanguageModelGateway gateway =
            new LlamaLanguageModelGateway(
                    jsonMapper,
                    "http://127.0.0.1:8092",
                    "local-model"
            );

    @Test
    void renameSchemaAllowsOnlyRenameAndRequiresOperands() {
        Map<String, Object> schema =
                gateway.schema(AssistantActionType.RENAME_CLASS);

        @SuppressWarnings("unchecked")
        Map<String, Object> rootProperties =
                (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> actions =
                (Map<String, Object>) rootProperties.get("actions");
        @SuppressWarnings("unchecked")
        Map<String, Object> item =
                (Map<String, Object>) actions.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
                (Map<String, Object>) item.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> type =
                (Map<String, Object>) properties.get("type");

        assertEquals(List.of("RENAME_CLASS"), type.get("enum"));
        assertEquals(1, actions.get("maxItems"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) item.get("required");
        assertTrue(required.contains("className"));
        assertTrue(required.contains("newName"));
    }

    @Test
    void renameContextDoesNotExposeExistingAttributesToTheModel()
            throws Exception {
        UmlClass veterinarian = new UmlClass(
                UUID.randomUUID(),
                "Veterinario",
                List.of(
                        new UmlAttribute(
                                UUID.randomUUID(),
                                "matricula",
                                UmlDataType.STRING,
                                null,
                                UmlVisibility.PRIVATE,
                                false,
                                false
                        )
                )
        );

        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(veterinarian), List.of()),
                DiagramLayout.empty()
        );

        JsonNode context = jsonMapper.readTree(
                gateway.compactContext(
                        "Renombra Veterinario a MedicoVeterinario",
                        document
                )
        );

        assertEquals(
                "RENAME_CLASS",
                context.get("intentHint").get("actionType").asString()
        );

        JsonNode focused = context.get("focusedClasses");
        assertEquals("Veterinario", focused.get(0).get("name").asString());
        assertFalse(focused.get(0).has("attributes"));
        assertFalse(context.has("relationships"));
    }

    @Test
    void attributeUpdateKeepsRelevantAttributesVisible()
            throws Exception {
        UmlClass pet = new UmlClass(
                UUID.randomUUID(),
                "Mascota",
                List.of(
                        new UmlAttribute(
                                UUID.randomUUID(),
                                "peso",
                                UmlDataType.DECIMAL,
                                null,
                                UmlVisibility.PRIVATE,
                                true,
                                false
                        )
                )
        );

        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(pet), List.of()),
                DiagramLayout.empty()
        );

        JsonNode context = jsonMapper.readTree(
                gateway.compactContext(
                        "En Mascota renombra el atributo peso a pesoKg",
                        document
                )
        );

        assertEquals(
                "UPDATE_ATTRIBUTE",
                context.get("intentHint").get("actionType").asString()
        );
        assertTrue(context.get("focusedClasses").get(0).has("attributes"));
    }
}
