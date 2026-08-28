package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaLanguageModelGatewayFocusedContextTests {

    private final JsonMapper jsonMapper =
            JsonMapper.builder()
                    .build();

    private final LlamaLanguageModelGateway gateway =
            new LlamaLanguageModelGateway(
                    jsonMapper,
                    "http://127.0.0.1:8092",
                    "local-model"
            );

    @Test
    void relationshipRequestIncludesMentionedClassesAndDirectNeighbors()
            throws Exception {
        UmlClass animal = umlClass("Animal");
        UmlClass veterinario = umlClass("Veterinario");
        UmlClass propietario = umlClass("Propietario");
        UmlClass cita = umlClass("Cita");

        ProjectDocument document =
                document(
                        List.of(
                                animal,
                                veterinario,
                                propietario,
                                cita
                        ),
                        List.of(
                                relationship(
                                        animal,
                                        propietario
                                ),
                                relationship(
                                        veterinario,
                                        cita
                                )
                        )
                );

        JsonNode context =
                jsonMapper.readTree(
                        gateway.compactContext(
                                "Conecta Animal con Veterinario",
                                document
                        )
                );

        JsonNode focused =
                context.get(
                        "focusedClasses"
                );

        assertTrue(
                containsClass(
                        focused,
                        "Animal"
                )
        );

        assertTrue(
                containsClass(
                        focused,
                        "Veterinario"
                )
        );

        assertTrue(
                containsClass(
                        focused,
                        "Propietario"
                )
        );

        assertTrue(
                containsClass(
                        focused,
                        "Cita"
                )
        );

        assertEquals(
                4,
                context.get(
                                "classCount"
                        )
                        .asInt()
        );
    }

    @Test
    void createNewClassDoesNotDumpExistingClassDetails()
            throws Exception {
        ProjectDocument document =
                document(
                        List.of(
                                umlClass("Animal"),
                                umlClass("Veterinario"),
                                umlClass("Propietario")
                        ),
                        List.of()
                );

        JsonNode context =
                jsonMapper.readTree(
                        gateway.compactContext(
                                "Crea una clase Cita con fecha Date",
                                document
                        )
                );

        assertTrue(
                context.has(
                        "existingClassNames"
                )
        );

        assertFalse(
                context.has(
                        "focusedClasses"
                )
        );
    }

    @Test
    void entityMatchingUnderstandsCommonPlural() {
        assertTrue(
                gateway.mentionsEntity(
                        "Relaciona veterinarios con animales",
                        "Veterinario"
                )
        );

        assertTrue(
                gateway.mentionsEntity(
                        "Relaciona veterinarios con animales",
                        "Animal"
                )
        );
    }

    private boolean containsClass(
            JsonNode classes,
            String name
    ) {
        if (classes == null) {
            return false;
        }

        for (
                JsonNode item
                : classes
        ) {
            if (
                    name.equals(
                            item.get(
                                            "name"
                                    )
                                    .asString()
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private UmlClass umlClass(
            String name
    ) {
        return new UmlClass(
                UUID.randomUUID(),
                name,
                List.of()
        );
    }

    private UmlRelationship relationship(
            UmlClass source,
            UmlClass target
    ) {
        return new UmlRelationship(
                UUID.randomUUID(),
                source.id(),
                target.id(),
                UmlRelationshipType.ASSOCIATION,
                new Multiplicity(
                        1,
                        1
                ),
                new Multiplicity(
                        1,
                        1
                )
        );
    }

    private ProjectDocument document(
            List<UmlClass> classes,
            List<UmlRelationship> relationships
    ) {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        classes,
                        relationships
                ),
                DiagramLayout.empty()
        );
    }
}